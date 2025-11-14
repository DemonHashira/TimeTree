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
 * Command to initialize a new TimeTree repository.
 */
class InitCmd(
    private val repoProvider: () -> RepoLayout = RepoLayout::fromWorkingDir,
) : CliktCommand(name = "init") {
    private val defaultBranch = "master"

    override fun help(context: Context) = "Create a new TimeTree repository"

    override fun run() {
        val repo = repoProvider()

        if (Files.exists(repo.meta) && !Files.isDirectory(repo.meta)) {
            echo("${Color.red("Error:")} ${repo.meta} exists but is not a directory", err = true)
            throw ProgramResult(1)
        }

        if (!Files.isWritable(repo.root)) {
            echo("${Color.red("Error:")} No write permission in ${repo.root}", err = true)
            throw ProgramResult(1)
        }

        try {
            val created = ensureInitialized(repo, defaultBranch)

            if (created) {
                echo("Initialized TimeTree repo in ${repo.meta}")
            } else {
                echo("Reinitialized existing TimeTree repository in ${repo.meta}")
            }
        } catch (e: IOException) {
            echo("${Color.red("Error:")} Failed to initialize repository: ${e.message}", err = true)
            throw ProgramResult(1)
        }
    }
}
