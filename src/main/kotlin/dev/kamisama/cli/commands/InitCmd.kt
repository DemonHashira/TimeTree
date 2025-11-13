package dev.kamisama.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import dev.kamisama.cli.Color
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.fs.ensureInitialized
import java.io.IOException
import java.nio.file.Files

/**
 * The command that initializes a new TimeTree repository.
 */
class InitCmd(
    // Provider function for repository layout, defaults to the current working directory
    private val repoProvider: () -> RepoLayout = RepoLayout::fromWorkingDir,
) : CliktCommand(name = "init") {
    // Default branch name for newly initialized repositories
    private val defaultBranch = "master"

    override fun help(context: Context) = "Initialize a new TimeTree repository in the current directory"

    override fun run() {
        val repo = repoProvider()

        // Validate preconditions before attempting initialization
        if (Files.exists(repo.meta) && !Files.isDirectory(repo.meta)) {
            echo("${Color.red("Error:")} ${repo.meta} exists but is not a directory", err = true)
            throw ProgramResult(1)
        }

        if (!Files.isWritable(repo.root)) {
            echo("${Color.red("Error:")} No write permission in ${repo.root}", err = true)
            throw ProgramResult(1)
        }

        try {
            // Initialize or repair the repository
            val created = ensureInitialized(repo, defaultBranch)

            // Provide appropriate feedback
            if (created) {
                echo("${Color.green("Initialized TimeTree repo")} in ${repo.meta}")
            } else {
                echo("${Color.yellow("Reinitialized existing TimeTree repository")} in ${repo.meta}")
            }
        } catch (e: IOException) {
            echo("${Color.red("Error:")} Failed to initialize repository: ${e.message}", err = true)
            throw ProgramResult(1)
        }
    }
}
