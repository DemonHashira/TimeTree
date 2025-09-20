package dev.kamisama.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.fs.ensureInitialized

class TimeTreeCli : CliktCommand(name = "timetree") {
    override fun help(context: Context) = "TimeTree – lightweight version control"

    init {
        subcommands(InitCmd())
    }

    override fun run() = Unit
}

class InitCmd(
    private val repoProvider: () -> RepoLayout = RepoLayout::fromWorkingDir,
) : CliktCommand(name = "init") {
    private val defaultBranch = "master"

    override fun help(context: Context) = "Initialize a new TimeTree repository in the current directory"

    override fun run() {
        val repo = repoProvider()
        val created = ensureInitialized(repo, defaultBranch)

        if (created) {
            echo("Initialized TimeTree repo in ${repo.meta}")
        } else {
            echo("Reinitialized existing TimeTree repository in ${repo.meta}")
        }
    }
}
