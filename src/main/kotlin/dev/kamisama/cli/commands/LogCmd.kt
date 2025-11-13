package dev.kamisama.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.kamisama.cli.CliUtils
import dev.kamisama.cli.Color
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.log.Log
import dev.kamisama.core.refs.Refs

class LogCmd(
    private val repoProvider: () -> RepoLayout = RepoLayout::fromWorkingDir,
) : CliktCommand(name = "log") {
    private val maxCount by option("-n", "--max-count", help = "Limit the number of commits to show")
        .default("0")
    private val all by option("--all", help = "Show all commits from all branches")
        .flag(default = false)

    override fun help(context: Context) = "Show commit history"

    override fun run() {
        val repo = repoProvider()
        CliUtils.requireRepository(repo)

        val limit = maxCount.toIntOrNull()?.takeIf { it > 0 }
        val commits = Log.getHistory(repo, limit, all)

        if (commits.isEmpty()) {
            echo("No commits yet in the repository")
            return
        }

        // Get all branches and HEAD for the reference display
        val branches = Refs.listBranches(repo)
        val head = Refs.readHead(repo)
        val currentBranch = head.currentBranch()

        // Create a map of a commit ID -> list of branch names
        val commitToBranches = mutableMapOf<String, MutableList<String>>()
        for ((branchName, commitId) in branches) {
            val commitHex = commitId.toHex()
            commitToBranches.computeIfAbsent(commitHex) { mutableListOf() }.add(branchName)
        }

        // Display commits in reverse chronological order
        for ((index, commit) in commits.withIndex()) {
            // Build reference string (e.g., "(HEAD -> master, feature)")
            val refs = buildReferenceString(commit.id.toHex(), commitToBranches, head, currentBranch)

            // Display commit with references
            if (refs.isNotEmpty()) {
                echo("commit ${commit.abbreviatedId()} $refs")
            } else {
                echo("commit ${commit.abbreviatedId()}")
            }

            echo("Author: ${commit.author} <${commit.authorEmail}>")
            echo("Date:   ${commit.formattedTimestamp()}")
            echo("")
            echo("    ${commit.firstLineOfMessage()}")

            if (index < commits.size - 1) {
                echo("")
            }
        }
    }

    /**
     * Build a reference string like "(HEAD -> master, feature)" with colors.
     */
    private fun buildReferenceString(
        commitHex: String,
        commitToBranches: Map<String, List<String>>,
        head: Refs.Head,
        currentBranch: String?,
    ): String {
        val refParts = mutableListOf<String>()

        // Get branches pointing to this commit
        val branchesAtCommit = commitToBranches[commitHex] ?: emptyList()

        // Check if HEAD points to this commit
        if (head.id?.toHex() == commitHex) {
            if (currentBranch != null && currentBranch in branchesAtCommit) {
                // HEAD points to a branch which points to this commit
                refParts.add("${Color.yellow("HEAD")} -> ${Color.green(currentBranch)}")
                // Add other branches (excluding the current one)
                branchesAtCommit.filter { it != currentBranch }.sorted().forEach {
                    refParts.add(Color.green(it))
                }
            } else {
                // Detached HEAD
                refParts.add(Color.yellow("HEAD"))
                // Add all branches
                branchesAtCommit.sorted().forEach {
                    refParts.add(Color.green(it))
                }
            }
        } else {
            // HEAD doesn't point here, just show branches
            branchesAtCommit.sorted().forEach {
                refParts.add(Color.green(it))
            }
        }

        return if (refParts.isNotEmpty()) {
            "(${refParts.joinToString(", ")})"
        } else {
            ""
        }
    }
}
