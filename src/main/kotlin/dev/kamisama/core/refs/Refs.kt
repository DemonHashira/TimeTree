package dev.kamisama.core.refs

import dev.kamisama.core.fs.AtomicFile
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Manages HEAD and branch references in the repository.
 */
object Refs {
    data class Head(
        val refPath: String?,
        val id: ObjectId?,
    ) {
        // Returns the current branch name or null if detached.
        fun currentBranch(): String? = refPath?.removePrefix("refs/heads/")
    }

    // Read the current HEAD state.
    fun readHead(repo: RepoLayout): Head {
        if (!Files.exists(repo.head)) return Head(refPath = "refs/heads/master", id = null)
        val s = Files.readString(repo.head, StandardCharsets.UTF_8).trim()
        return if (s.startsWith("ref: ")) {
            val ref = s.removePrefix("ref: ").trim()
            val p = repo.meta.resolve(ref)
            val id = if (Files.exists(p)) ObjectId.fromHex(Files.readString(p).trim()) else null
            Head(refPath = ref, id = id)
        } else {
            Head(refPath = null, id = ObjectId.fromHex(s))
        }
    }

    // Ensure HEAD points to the given branch ref (used on the first commit in fresh repo).
    fun ensureHeadOn(
        repo: RepoLayout,
        branchRef: String = "refs/heads/master",
    ) {
        AtomicFile(repo.head).writeUtf8("ref: $branchRef\n")
    }

    // Update a ref atomically to a new commit id.
    fun updateRef(
        repo: RepoLayout,
        refPath: String,
        idHex: String,
    ) {
        val p = repo.meta.resolve(refPath)
        if (!Files.isDirectory(p.parent)) Files.createDirectories(p.parent)
        AtomicFile(p).writeUtf8("$idHex\n")
    }

    // List all local branches.
    fun listBranches(repo: RepoLayout): Map<String, ObjectId> {
        val branches = mutableMapOf<String, ObjectId>()
        val headsDir = repo.refsHeads

        if (!Files.exists(headsDir)) {
            return emptyMap()
        }

        Files.walk(headsDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .forEach { branchFile ->
                    val branchName = headsDir.relativize(branchFile).toString()
                    val commitHex = Files.readString(branchFile, StandardCharsets.UTF_8).trim()
                    branches[branchName] = ObjectId.fromHex(commitHex)
                }
        }

        return branches
    }

    // Create a new branch pointing to the specified commit.
    fun createBranch(
        repo: RepoLayout,
        branchName: String,
        commitId: ObjectId?,
    ) {
        val head = readHead(repo)
        val targetCommit =
            commitId ?: head.id
                ?: throw IllegalStateException("Cannot create branch: no commits yet and no target specified")

        val branchRef = "refs/heads/$branchName"
        updateRef(repo, branchRef, targetCommit.toHex())
    }

    // Delete a branch.
    fun deleteBranch(
        repo: RepoLayout,
        branchName: String,
    ): Boolean {
        val branchPath = repo.meta.resolve("refs/heads/$branchName")
        return if (Files.exists(branchPath)) {
            Files.delete(branchPath)
            true
        } else {
            false
        }
    }

    // Check if a branch exists.
    fun branchExists(
        repo: RepoLayout,
        branchName: String,
    ): Boolean {
        val branchPath = repo.meta.resolve("refs/heads/$branchName")
        return Files.exists(branchPath)
    }

    // Get the commit ID for a branch.
    fun getBranchCommit(
        repo: RepoLayout,
        branchName: String,
    ): ObjectId? {
        val branchPath = repo.meta.resolve("refs/heads/$branchName")
        return if (Files.exists(branchPath)) {
            val commitHex = Files.readString(branchPath, StandardCharsets.UTF_8).trim()
            ObjectId.fromHex(commitHex)
        } else {
            null
        }
    }
}
