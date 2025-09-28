package dev.kamisama.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

object CommandRouter {
    fun build(): CliktCommand =
        TimeTreeCli()
            .subcommands(
                InitCmd(),
                AddCmd(),
            )
}
