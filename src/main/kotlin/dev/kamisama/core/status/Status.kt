package dev.kamisama.core.status

import dev.kamisama.core.diff.Diff
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import dev.kamisama.core.index.Index
import dev.kamisama.core.objects.FsObjectStore
import dev.kamisama.core.refs.Refs
import java.io.File
import java.nio.file.Files
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence

/**
 * Represents the status of the repository
 */
data class StatusResult(
    val staged: List<String> = emptyList(),
    val unstaged: List<String> = emptyList(),
    val untracked: List<String> = emptyList(),
)

// Computes the repository status by comparing the working tree, index, and HEAD.
object Status {
    // Compute the status of the repository.
    fun compute(repo: RepoLayout): StatusResult {
        val (root, _) = repo.normalizedPaths()

        val index = Index.load(repo)
        val headTree = loadHeadTree(repo)

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

        // Lazy evaluation, only hash tracked files to check for modifications
        for ((path, indexId) in index) {
            val filePath = root.resolve(path)
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                val workingHash = FsObjectStore.computeBlobHash(filePath)
                if (workingHash != indexId) {
                    unstaged.add(path)
                }
            } else {
                unstaged.add(path)
            }
        }

        // Collect untracked files (files not in index)
        untracked.addAll(collectUntrackedFiles(repo, index.keys))

        return StatusResult(
            staged = staged.sorted(),
            unstaged = unstaged.sorted(),
            untracked = untracked.sorted(),
        )
    }

    // Load the tree from HEAD commit if it exists.
    private fun loadHeadTree(repo: RepoLayout): Map<String, ObjectId> {
        val head = Refs.readHead(repo)
        val commitId = head.id ?: return emptyMap()

        val treeId = Diff.readCommitTree(repo, commitId) ?: return emptyMap()
        return Diff.parseTree(repo, treeId, "")
    }

    // Collects untracked files in the working tree.
    private fun collectUntrackedFiles(
        repo: RepoLayout,
        trackedFiles: Set<String>,
    ): List<String> {
        val (root, meta) = repo.normalizedPaths()
        val untracked = mutableListOf<String>()

        Files
            .walk(root)
            .asSequence()
            .filter { it.isRegularFile() }
            .filter { !it.startsWith(meta) }
            .forEach { path ->
                val relativePath = root.relativize(path).toString().replace(File.separatorChar, '/')
                if (relativePath !in trackedFiles) {
                    untracked.add(relativePath)
                }
            }

        return untracked
    }
}
