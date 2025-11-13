package dev.kamisama.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import dev.kamisama.core.delta.io.DeltaIO
import dev.kamisama.core.delta.RsyncDelta
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Paths

/**
 * Apply a delta to a basis file to reconstruct the target.
 */
class PatchCmd : CliktCommand(name = "patch") {
    private val basis by argument()
    private val deltaFile by argument()
    private val output by option("-o", "--output").default("reconstructed.bin")

    override fun help(context: Context) = "Apply a delta to a basis file to reconstruct the target"

    override fun run() {
        val basisPath = Paths.get(basis)

        val delta =
            FileInputStream(deltaFile).use { input ->
                DeltaIO.read(input)
            }

        val algo = RsyncDelta()
        FileOutputStream(output).use { out ->
            algo.applyDelta(basisPath, delta, out)
        }

        echo("Reconstructed file: $output")
    }
}
