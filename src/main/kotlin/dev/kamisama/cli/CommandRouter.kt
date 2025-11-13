package dev.kamisama.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import dev.kamisama.cli.commands.AddCmd
import dev.kamisama.cli.commands.BranchCmd
import dev.kamisama.cli.commands.CheckoutCmd
import dev.kamisama.cli.commands.CommitCmd
import dev.kamisama.cli.commands.DeltaCmd
import dev.kamisama.cli.commands.DiffCmd
import dev.kamisama.cli.commands.HashObjectCmd
import dev.kamisama.cli.commands.InitCmd
import dev.kamisama.cli.commands.LogCmd
import dev.kamisama.cli.commands.PatchCmd
import dev.kamisama.cli.commands.SigCmd
import dev.kamisama.cli.commands.StatusCmd

object CommandRouter {
    fun build(): CliktCommand =
        TimeTreeCli()
            .subcommands(
                InitCmd(),
                AddCmd(),
                HashObjectCmd(),
                CommitCmd(),
                StatusCmd(),
                LogCmd(),
                BranchCmd(),
                CheckoutCmd(),
                DiffCmd(),
                SigCmd(),
                DeltaCmd(),
                PatchCmd(),
            )
}
