package dev.kamisama.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.status.Status
import java.nio.file.Files

class StatusCmd(
    private val repoProvider: () -> RepoLayout = RepoLayout::fromWorkingDir,
) : CliktCommand(name = "status") {
    override fun help(context: Context) = "Show the working tree status"

    override fun run() {
        val repo = repoProvider()
        require(Files.isDirectory(repo.meta)) { "Not a TimeTree repository (no .timetree directory)" }

        val status = Status.compute(repo)

        // Display results in a format similar to git status
        if (status.staged.isEmpty() && status.unstaged.isEmpty() && status.untracked.isEmpty()) {
            echo("nothing to commit, working tree clean")
            return
        }

        // Staged changes
        if (status.staged.isNotEmpty()) {
            echo("Changes to be committed:")
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
            echo("Changes not staged for commit:")
            echo("  (use \"timetree add <file>...\" to update what will be committed)")
            echo("")
            for (file in status.unstaged) {
                echo("        modified:   $file")
            }
            echo("")
        }

        // Untracked files
        if (status.untracked.isNotEmpty()) {
            echo("Untracked files:")
            echo("  (use \"timetree add <file>...\" to include in what will be committed)")
            for (file in status.untracked) {
                echo("        $file")
            }
        }
    }
}
