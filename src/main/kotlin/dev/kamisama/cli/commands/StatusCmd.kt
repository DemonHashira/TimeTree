package dev.kamisama.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import dev.kamisama.cli.CliUtils
import dev.kamisama.cli.Color
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.status.Status

class StatusCmd(
    private val repoProvider: () -> RepoLayout = RepoLayout::fromWorkingDir,
) : CliktCommand(name = "status") {
    override fun help(context: Context) = "Show changed and untracked files"

    override fun run() {
        val repo = repoProvider()
        CliUtils.requireRepository(repo)

        val status = Status.compute(repo)

        // Display results in a format similar to git status
        if (status.staged.isEmpty() && status.unstaged.isEmpty() && status.untracked.isEmpty()) {
            echo(Color.green("nothing to commit, working tree clean"))
            return
        }

        // Staged changes
        if (status.staged.isNotEmpty()) {
            echo(Color.green("Changes to be committed:"))
            echo("")
            for (file in status.staged) {
                echo("        modified:   $file")
            }
            if (status.unstaged.isNotEmpty() || status.untracked.isNotEmpty()) {
                echo("")
            }
        }

        // Unstaged changes
        if (status.unstaged.isNotEmpty()) {
            echo(Color.red("Changes not staged for commit:"))
            echo("  (use \"timetree add <file>...\" to update what will be committed)")
            echo("")
            for (file in status.unstaged) {
                echo("        modified:   $file")
            }
            if (status.untracked.isNotEmpty() && status.staged.isEmpty()) {
                echo("")
            }
        }

        // Untracked files
        if (status.untracked.isNotEmpty()) {
            echo(Color.red("Untracked files:"))
            echo("  (use \"timetree add <file>...\" to include in what will be committed)")
            for (file in status.untracked) {
                echo("        $file")
            }
        }
    }
}
