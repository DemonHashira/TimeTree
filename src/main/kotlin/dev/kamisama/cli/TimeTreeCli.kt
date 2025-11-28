package dev.kamisama.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context

/**
 * Root CLI command that delegates to subcommands.
 */
class TimeTreeCli : CliktCommand(name = "timetree") {
    override fun help(context: Context) = "TimeTree - lightweight version control"

    override fun run() = Unit
}
