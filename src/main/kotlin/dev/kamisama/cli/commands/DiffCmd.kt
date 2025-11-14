package dev.kamisama.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.kamisama.cli.CliUtils
import dev.kamisama.cli.Color
import dev.kamisama.core.diff.Diff
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import dev.kamisama.core.index.Index
import dev.kamisama.core.objects.FsObjectStore
import dev.kamisama.core.refs.Refs
import java.nio.file.Files

/**
 * The diff command shows line-level changes between two versions.
 */
class DiffCmd(
    private val repoProvider: () -> RepoLayout = RepoLayout::fromWorkingDir,
) : CliktCommand(name = "diff") {
    private val commit1 by argument(name = "commit1").optional()
    private val commit2 by argument(name = "commit2").optional()

    private val contextLines by option(
        "-U",
        "--unified",
        help = "Number of context lines to show",
    ).default("3")

    private val cached by option(
        "--cached",
        "--staged",
        help = "Compare staged changes with HEAD",
    ).flag(default = false)

    override fun help(context: Context) = "Show file differences"

    override fun run() {
        val repo = repoProvider()
        CliUtils.requireRepository(repo)

        val context = contextLines.toIntOrNull() ?: 3
        if (context < 0) {
            echo("${Color.red("Error:")} context lines must be non-negative", err = true)
            throw ProgramResult(1)
        }

        try {
            when {
                // Compare two commits
                commit1 != null && commit2 != null -> {
                    val id1 = resolveCommit(repo, commit1!!)
                    val id2 = resolveCommit(repo, commit2!!)
                    showDiff(repo, id1, id2, context)
                }
                // Compare one commit with HEAD
                commit1 != null && commit2 == null -> {
                    val id1 = resolveCommit(repo, commit1!!)
                    val head = Refs.readHead(repo)
                    if (head.id == null) {
                        echo("${Color.red("Error:")} no commits yet", err = true)
                        throw ProgramResult(1)
                    }
                    showDiff(repo, id1, head.id, context)
                }
                // Compare HEAD with the working directory (or staged if --cached)
                else -> {
                    if (cached) {
                        showStagedDiff(repo, context)
                    } else {
                        showWorkingDiff(repo, context)
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            echo("${Color.red("Error:")} ${e.message}", err = true)
            throw ProgramResult(1)
        }
    }

    private fun showDiff(
        repo: RepoLayout,
        oldCommitId: ObjectId?,
        newCommitId: ObjectId?,
        contextLines: Int,
    ) {
        val diff = Diff.diffCommits(repo, oldCommitId, newCommitId, contextLines)

        if (diff.isEmpty()) {
            echo("No differences found")
        } else {
            echo(colorizeDiff(diff.trimEnd()))
        }
    }

    /**
     * Show differences between index and working directory (unstaged changes).
     */
    private fun showWorkingDiff(
        repo: RepoLayout,
        contextLines: Int,
    ) {
        val index = Index.load(repo)
        val root = repo.root.toAbsolutePath().normalize()

        if (index.isEmpty()) {
            echo("No files tracked. Use 'timetree add' to track files.")
            return
        }

        val result = StringBuilder()
        var hasChanges = false

        for ((path, indexId) in index.entries.sortedBy { it.key }) {
            val filePath = root.resolve(path)

            if (!Files.exists(filePath)) {
                // File deleted
                hasChanges = true
                val diff = Diff.diffBlobs(repo, indexId, null, path, contextLines)
                result.append(diff)
            } else if (Files.isRegularFile(filePath)) {
                // Check if a file is modified
                val workingHash = FsObjectStore.computeBlobHash(filePath)
                if (workingHash != indexId) {
                    hasChanges = true
                    // Create a temporary blob for the working directory version
                    val workingBlob = FsObjectStore.writeBlob(repo, filePath)
                    val diff = Diff.diffBlobs(repo, indexId, workingBlob, path, contextLines)
                    result.append(diff)
                }
            }
        }

        if (!hasChanges) {
            echo("No changes in working directory")
        } else {
            echo(colorizeDiff(result.toString().trimEnd()))
        }
    }

    /**
     * Show differences between HEAD and index (staged changes).
     */
    private fun showStagedDiff(
        repo: RepoLayout,
        contextLines: Int,
    ) {
        val head = Refs.readHead(repo)
        if (head.id == null) {
            echo("No HEAD commit yet. All staged changes will be in the first commit.")
            return
        }

        val index = Index.load(repo)
        val treeId = Diff.readCommitTree(repo, head.id) ?: return
        val headTree = Diff.parseTree(repo, treeId)

        if (index.isEmpty() && headTree.isEmpty()) {
            echo("No changes staged")
            return
        }

        val result = StringBuilder()
        var hasChanges = false

        // Check all files in index and HEAD
        val allPaths = (index.keys + headTree.keys).toSortedSet()

        for (path in allPaths) {
            val indexId = index[path]
            val headId = headTree[path]

            if (indexId != headId) {
                hasChanges = true
                val diff = Diff.diffBlobs(repo, headId, indexId, path, contextLines)
                result.append(diff)
            }
        }

        if (!hasChanges) {
            echo("No changes staged")
        } else {
            echo(colorizeDiff(result.toString().trimEnd()))
        }
    }

    /**
     * Resolve a commit reference (branch name, commit hash, or HEAD).
     */
    private fun resolveCommit(
        repo: RepoLayout,
        ref: String,
    ): ObjectId {
        // Try as a branch name first
        if (Refs.branchExists(repo, ref)) {
            return Refs.getBranchCommit(repo, ref)
                ?: throw IllegalArgumentException("Branch '$ref' has no commits")
        }

        // Try as a commit hash (full or abbreviated)
        if (ref.matches(Regex("[0-9a-f]{6,40}"))) {
            // Look for an object in the object store
            val possibleMatches = findObjectsWithPrefix(repo, ref)
            when {
                possibleMatches.isEmpty() -> {
                    throw IllegalArgumentException("No commit found matching '$ref'")
                }
                possibleMatches.size > 1 -> {
                    throw IllegalArgumentException(
                        "Ambiguous reference '$ref' matches multiple commits: ${possibleMatches.joinToString(", ")}",
                    )
                }
                else -> {
                    return ObjectId.fromHex(possibleMatches.first())
                }
            }
        }

        // Check if it's HEAD
        if (ref.equals("HEAD", ignoreCase = true)) {
            val head = Refs.readHead(repo)
            return head.id ?: throw IllegalArgumentException("HEAD has no commits yet")
        }

        throw IllegalArgumentException("Unknown reference: '$ref'")
    }

    /**
     * Find all objects in the object store that start with the given prefix.
     */
    private fun findObjectsWithPrefix(
        repo: RepoLayout,
        prefix: String,
    ): List<String> {
        if (prefix.length < 2) {
            return emptyList()
        }

        val dir = repo.objects.resolve(prefix.take(2))
        if (!Files.exists(dir)) {
            return emptyList()
        }

        val matches = mutableListOf<String>()
        val remainder = prefix.substring(2)

        Files.list(dir).use { stream ->
            stream.forEach { file ->
                val filename = file.fileName.toString()
                if (filename.startsWith(remainder)) {
                    matches.add(prefix.take(2) + filename)
                }
            }
        }

        return matches
    }

    private fun colorizeDiff(diff: String): String {
        if (!Color.enabled) {
            return diff
        }

        return diff.lines().joinToString("\n") { line ->
            when {
                line.startsWith("new file") -> Color.green(line)
                line.startsWith("deleted file") -> Color.red(line)
                else -> line
            }
        }
    }
}
