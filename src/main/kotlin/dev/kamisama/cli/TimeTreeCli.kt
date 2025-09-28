package dev.kamisama.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context

/**
 * Main CLI command class for the TimeTree version control system.
 * This serves as the root command that coordinates subcommands like init, add, etc.
 */
class TimeTreeCli : CliktCommand(name = "timetree") {
    // Provide help text displayed when the user runs 'timetree --help'
    override fun help(context: Context) = "TimeTree – lightweight version control"

    // Root command doesn't perform any action by itself
    // It delegates to subcommands (init, add, commit, etc.)
    override fun run() = Unit
}
