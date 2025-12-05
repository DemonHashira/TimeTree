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
 * Handles checkout of branches and commits.
 */
object Checkout {
    // Switches to a branch by updating HEAD, index, and working tree.
    fun checkoutBranch(
        repo: RepoLayout,
        branchName: String,
    ) {
        if (!Refs.branchExists(repo, branchName)) {
            throw IllegalArgumentException("Branch '$branchName' does not exist")
        }

        val commitId =
            Refs.getBranchCommit(repo, branchName)
                ?: throw IllegalStateException("Branch '$branchName' exists but has no commit")

        val head = Refs.readHead(repo)
        if (head.currentBranch() == branchName) {
            return
        }

        Refs.ensureHeadOn(repo, "refs/heads/$branchName")
        updateWorkingTreeToCommit(repo, commitId)
    }

    // Checks out a specific commit in a detached HEAD state.
    fun checkoutCommit(
        repo: RepoLayout,
        commitId: ObjectId,
    ) {
        val headPath = repo.head
        Files.writeString(headPath, "${commitId.toHex()}\n", StandardCharsets.UTF_8)
        updateWorkingTreeToCommit(repo, commitId)
    }

    // Updates working tree and index to match a commit.
    private fun updateWorkingTreeToCommit(
        repo: RepoLayout,
        commitId: ObjectId,
    ) {
        val treeId =
            Diff.readCommitTree(repo, commitId)
                ?: throw IllegalStateException("Commit ${commitId.toHex()} missing 'tree' header")

        val treeEntries = Diff.parseTree(repo, treeId, "")
        clearWorkingTree(repo)

        val (root, _) = repo.normalizedPaths()
        for ((path, blobId) in treeEntries) {
            val filePath = root.resolve(path)
            Files.createDirectories(filePath.parent)
            Files.newOutputStream(filePath).use { out ->
                DeltaStore.streamBlobContent(repo, blobId, out)
            }
        }

        updateIndexFromTree(repo, treeEntries)
    }

    // Removes all files from the working tree except .timetree.
    private fun clearWorkingTree(repo: RepoLayout) {
        val (root, meta) = repo.normalizedPaths()
        val directoriesToRemove = mutableListOf<Path>()

        Files.walk(root).use { stream ->
            stream.forEach { path ->
                if (path.startsWith(meta)) return@forEach

                when {
                    Files.isRegularFile(path) -> {
                        Files.delete(path)
                    }

                    Files.isDirectory(path) && path != root && path != meta -> {
                        directoriesToRemove.add(path)
                    }
                }
            }
        }

        directoriesToRemove.sortedDescending().forEach { Files.delete(it) }
    }

    // Replaces index with tree entries from a commit.
    private fun updateIndexFromTree(
        repo: RepoLayout,
        treeEntries: Map<String, ObjectId>,
    ) {
        val indexPath = repo.meta.resolve("index")
        if (Files.exists(indexPath)) {
            Files.delete(indexPath)
        }

        for ((path, blobId) in treeEntries) {
            Index.update(repo, path, blobId)
        }
    }
}
