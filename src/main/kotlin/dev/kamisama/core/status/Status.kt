package dev.kamisama.core.status

import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import dev.kamisama.core.index.Index
import dev.kamisama.core.objects.FsObjectStore
import dev.kamisama.core.refs.Refs
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence

/**
 * Represents the status of the repository by comparing:
 * - Working tree (files on disk)
 * - Index (staged files)
 * - HEAD commit (last committed state)
 */
data class StatusResult(
    val staged: List<String> = emptyList(),
    val unstaged: List<String> = emptyList(),
    val untracked: List<String> = emptyList(),
)

/**
 * Computes the repository status by comparing the working tree, index, and HEAD.
 */
object Status {
    /**
     * Compute the status of the repository.
     *
     * Returns a StatusResult with:
     * - staged: files where index differs from HEAD
     * - unstaged: files where the working tree differs from index
     * - untracked: files in the working tree but not in index
     */
    fun compute(repo: RepoLayout): StatusResult {
        // 1. Load index
        val index = Index.load(repo)

        // 2. Load HEAD tree (if it exists)
        val headTree = loadHeadTree(repo)

        // 3. Collect working tree files
        val workingFiles = collectWorkingFiles(repo)

        // 4. Classify files
        val staged = mutableListOf<String>()
        val unstaged = mutableListOf<String>()
        val untracked = mutableListOf<String>()

        // Check for staged files (index != HEAD)
        for ((path, indexId) in index) {
            val headId = headTree[path]
            if (headId == null || headId != indexId) {
                staged.add(path)
            }
        }

        // Check for files in HEAD but deleted from the index (also staged)
        for ((path, _) in headTree) {
            if (path !in index) {
                staged.add(path)
            }
        }

        // Check for unstaged and untracked files
        for ((path, workingHash) in workingFiles) {
            if (path in index) {
                // File is tracked - check if modified
                if (index[path] != workingHash) {
                    unstaged.add(path)
                }
            } else {
                // File is not in index - untracked
                untracked.add(path)
            }
        }

        // Check for deleted files (in the index but not in the working tree)
        for ((path, _) in index) {
            if (path !in workingFiles) {
                unstaged.add(path)
            }
        }

        return StatusResult(
            staged = staged.sorted(),
            unstaged = unstaged.sorted(),
            untracked = untracked.sorted(),
        )
    }

    /**
     * Load the tree from HEAD commit if it exists.
     * Returns a map of path -> ObjectId for all files in the HEAD tree.
     */
    private fun loadHeadTree(repo: RepoLayout): Map<String, ObjectId> {
        val head = Refs.readHead(repo)
        val commitId = head.id ?: return emptyMap()

        // Read the commit object to get tree id
        val treeId = readTreeIdFromCommit(repo, commitId)

        // Read and parse the tree recursively
        return readTreeRecursively(repo, treeId, "")
    }

    /**
     * Reads the tree ID from a commit object.
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
                ?: error("Commit ${commitId.toHex()} missing 'tree' header")
        return ObjectId.fromHex(treeHex)
    }

    /**
     * Recursively reads a tree object and returns all file paths with their object IDs.
     *
     * @param prefix The current directory path prefix (used for recursion)
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
     * Collects all files in the working tree with their computed object IDs.
     * Excludes the .timetree directory.
     * This is a read-only operation - it only computes hashes without writing to the object store.
     */
    private fun collectWorkingFiles(repo: RepoLayout): Map<String, ObjectId> {
        val root = repo.root.toAbsolutePath().normalize()
        val meta = repo.meta.toAbsolutePath().normalize()
        val result = mutableMapOf<String, ObjectId>()

        Files
            .walk(root)
            .asSequence()
            .filter { it.isRegularFile() }
            .filter { !it.startsWith(meta) }
            .forEach { path ->
                val relativePath = root.relativize(path).toString().replace(File.separatorChar, '/')
                // Compute the hash WITHOUT writing to the object store
                val id = FsObjectStore.computeBlobHash(path)
                result[relativePath] = id
            }

        return result
    }
}
