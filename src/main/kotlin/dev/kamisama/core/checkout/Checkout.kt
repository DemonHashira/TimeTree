package dev.kamisama.core.checkout

import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import dev.kamisama.core.index.Index
import dev.kamisama.core.refs.Refs
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Checkout functionality for switching branches or commits.
 */
object Checkout {
    /**
     * Checkout a branch or commit.
     * This updates HEAD, index, and working tree.
     */
    fun checkoutBranch(
        repo: RepoLayout,
        branchName: String,
    ) {
        // Check if a branch exists
        if (!Refs.branchExists(repo, branchName)) {
            throw IllegalArgumentException("Branch '$branchName' does not exist")
        }

        // Get the commit ID for this branch
        val commitId =
            Refs.getBranchCommit(repo, branchName)
                ?: throw IllegalStateException("Branch '$branchName' exists but has no commit")

        // Check if we're already on this branch
        val head = Refs.readHead(repo)
        if (head.currentBranch() == branchName) {
            // Already on this branch, nothing to do
            return
        }

        // Update HEAD to point to the branch
        Refs.ensureHeadOn(repo, "refs/heads/$branchName")

        // Update the working tree and index to match the target commit
        updateWorkingTreeToCommit(repo, commitId)
    }

    /**
     * Checkout a specific commit (detached HEAD).
     */
    fun checkoutCommit(
        repo: RepoLayout,
        commitId: ObjectId,
    ) {
        // Update HEAD to point directly to the commit (detached state)
        val headPath = repo.head
        Files.writeString(headPath, "${commitId.toHex()}\n", StandardCharsets.UTF_8)

        // Update the working tree and index to match the commit
        updateWorkingTreeToCommit(repo, commitId)
    }

    /**
     * Update the working tree and index to match a commit.
     */
    private fun updateWorkingTreeToCommit(
        repo: RepoLayout,
        commitId: ObjectId,
    ) {
        // Read the commit to get its tree
        val treeId = readTreeIdFromCommit(repo, commitId)

        // Read the tree to get all file entries
        val treeEntries = readTreeRecursively(repo, treeId, "")

        // Clear the current working tree (except .timetree)
        clearWorkingTree(repo)

        // Write all files from the tree to the working directory
        val root = repo.root.toAbsolutePath().normalize()
        for ((path, blobId) in treeEntries) {
            val filePath = root.resolve(path)

            // Create parent directories if needed
            Files.createDirectories(filePath.parent)

            // Read blob content and write to file
            val blobContent = readBlob(repo, blobId)
            Files.write(filePath, blobContent)
        }

        // Update the index to match the tree
        updateIndexFromTree(repo, treeEntries)
    }

    /**
     * Read tree ID from a commit object.
     */
    private fun readTreeIdFromCommit(
        repo: RepoLayout,
        commitId: ObjectId,
    ): ObjectId {
        val hex = commitId.toHex()
        val commitPath = repo.objects.resolve(hex.take(2)).resolve(hex.substring(2))
        val commitBody = Files.readString(commitPath, StandardCharsets.UTF_8)
        val treeHex =
            Regex("""(?m)^tree ([0-9a-f]{40})$""")
                .find(commitBody)
                ?.groupValues
                ?.get(1)
                ?: throw IllegalStateException("Commit ${commitId.toHex()} missing 'tree' header")
        return ObjectId.fromHex(treeHex)
    }

    /**
     * Recursively read a tree object and return all file paths with their blob IDs.
     */
    private fun readTreeRecursively(
        repo: RepoLayout,
        treeId: ObjectId,
        prefix: String,
    ): Map<String, ObjectId> {
        val result = mutableMapOf<String, ObjectId>()
        val hex = treeId.toHex()
        val treePath = repo.objects.resolve(hex.take(2)).resolve(hex.substring(2))

        if (!Files.exists(treePath)) {
            return emptyMap()
        }

        val treeContent = Files.readString(treePath, StandardCharsets.UTF_8)
        val lines = treeContent.lines().filter { it.isNotBlank() }

        for (line in lines) {
            // Format: "<mode> <name>\t<hex-id>"
            val parts = line.split('\t')
            if (parts.size != 2) continue

            val (modeAndName, hexId) = parts
            val spaceIdx = modeAndName.indexOf(' ')
            if (spaceIdx == -1) continue

            val mode = modeAndName.substring(0, spaceIdx)
            val name = modeAndName.substring(spaceIdx + 1)
            val id = ObjectId.fromHex(hexId)

            val fullPath = if (prefix.isEmpty()) name else "$prefix/$name"

            if (mode == "040000") {
                // Directory - recurse
                result.putAll(readTreeRecursively(repo, id, fullPath))
            } else {
                // File
                result[fullPath] = id
            }
        }

        return result
    }

    /**
     * Read blob content from the object store.
     */
    private fun readBlob(
        repo: RepoLayout,
        blobId: ObjectId,
    ): ByteArray {
        val hex = blobId.toHex()
        val blobPath = repo.objects.resolve(hex.take(2)).resolve(hex.substring(2))
        return Files.readAllBytes(blobPath)
    }

    /**
     * Clear the working tree (except .timetree directory).
     */
    private fun clearWorkingTree(repo: RepoLayout) {
        val root = repo.root.toAbsolutePath().normalize()
        val meta = repo.meta.toAbsolutePath().normalize()

        Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { !it.startsWith(meta) }
                .forEach { Files.delete(it) }
        }

        // Remove empty directories
        Files.walk(root).use { stream ->
            stream
                .filter { Files.isDirectory(it) }
                .filter { it != root && it != meta && !it.startsWith(meta) }
                .sorted(Comparator.reverseOrder())
                .forEach {
                    try {
                        Files.delete(it)
                    } catch (e: Exception) {
                        // Directory isn't empty, skip
                    }
                }
        }
    }

    /**
     * Update the index to match tree entries.
     */
    private fun updateIndexFromTree(
        repo: RepoLayout,
        treeEntries: Map<String, ObjectId>,
    ) {
        // Clear the current index by creating a new empty one
        val indexPath = repo.meta.resolve("index")
        if (Files.exists(indexPath)) {
            Files.delete(indexPath)
        }

        // Add all tree entries to the index
        for ((path, blobId) in treeEntries) {
            Index.update(repo, path, blobId)
        }
    }
}
