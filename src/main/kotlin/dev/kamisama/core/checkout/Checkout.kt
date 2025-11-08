package dev.kamisama.core.checkout

import dev.kamisama.core.diff.Diff
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import dev.kamisama.core.index.Index
import dev.kamisama.core.objects.DeltaStore
import dev.kamisama.core.refs.Refs
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

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
        val treeId =
            Diff.readCommitTree(repo, commitId)
                ?: throw IllegalStateException("Commit ${commitId.toHex()} missing 'tree' header")

        // Read the tree to get all file entries
        val treeEntries = Diff.parseTree(repo, treeId, "")

        // Clear the current working tree (except .timetree)
        clearWorkingTree(repo)

        // Write all files from the tree to the working directory
        val (root, _) = repo.normalizedPaths()
        for ((path, blobId) in treeEntries) {
            val filePath = root.resolve(path)
            Files.createDirectories(filePath.parent)
            Files.newOutputStream(filePath).use { out ->
                DeltaStore.streamBlobContent(repo, blobId, out)
            }
        }

        // Update the index to match the tree
        updateIndexFromTree(repo, treeEntries)
    }

    /**
     * Clear the working tree (except .timetree directory).
     */
    private fun clearWorkingTree(repo: RepoLayout) {
        val (root, meta) = repo.normalizedPaths()
        val directoriesToRemove = mutableListOf<Path>()

        Files.walk(root).use { stream ->
            stream.forEach { path ->
                if (path.startsWith(meta)) return@forEach

                when {
                    Files.isRegularFile(path) -> Files.delete(path)
                    Files.isDirectory(path) && path != root && path != meta -> {
                        directoriesToRemove.add(path)
                    }
                }
            }
        }

        // Remove directories in reverse order
        directoriesToRemove.sortedDescending().forEach {
            try {
                Files.delete(it)
            } catch (e: Exception) {
                // Directory isn't empty, skip
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
