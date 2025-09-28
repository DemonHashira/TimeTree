package dev.kamisama.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.fs.ensureInitialized

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
        // Get the repository layout (directories and structure)
        val repo = repoProvider()

        // Initialize the repository structure and return whether it was newly created
        // ensureInitialized creates the necessary directories (.timetree) and initial files
        val created = ensureInitialized(repo, defaultBranch)

        // Provide appropriate feedback based on whether this was a new or existing repo
        if (created) {
            echo("Initialized TimeTree repo in ${repo.meta}")
        } else {
            echo("Reinitialized existing TimeTree repository in ${repo.meta}")
        }
    }
}
