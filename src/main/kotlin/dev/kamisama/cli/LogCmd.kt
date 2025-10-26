package dev.kamisama.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.log.Log
import java.nio.file.Files

class LogCmd(
    private val repoProvider: () -> RepoLayout = RepoLayout::fromWorkingDir,
) : CliktCommand(name = "log") {
    private val maxCount by option("-n", "--max-count", help = "Limit the number of commits to show")
        .default("0")

    override fun help(context: Context) = "Show commit history"

    override fun run() {
        val repo = repoProvider()
        require(Files.isDirectory(repo.meta)) { "Not a TimeTree repository (no .timetree directory)" }

        val limit = maxCount.toIntOrNull()?.takeIf { it > 0 }
        val commits = Log.getHistory(repo, limit)

        if (commits.isEmpty()) {
            echo("No commits yet in the repository")
            return
        }

        // Display commits in reverse chronological order
        for ((index, commit) in commits.withIndex()) {
            echo("commit ${commit.abbreviatedId()}")
            echo("Author: ${commit.author} <${commit.authorEmail}>")
            echo("Date:   ${commit.formattedTimestamp()}")
            echo("")
            echo("    ${commit.firstLineOfMessage()}")

            if (index < commits.size - 1) {
                echo("")
            }
        }
    }
}
