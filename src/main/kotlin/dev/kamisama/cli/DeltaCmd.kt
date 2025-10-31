package dev.kamisama.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import dev.kamisama.core.delta.DeltaIO
import dev.kamisama.core.delta.RsyncDelta
import dev.kamisama.core.delta.SignatureIO
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Generate a delta file by comparing a target against a signature.
 */
class DeltaCmd : CliktCommand(name = "delta") {
    private val signatureFile by argument()
    private val target by argument()
    private val output by option("-o", "--output")

    override fun help(context: Context) = "Generate a delta from a signature and target file"

    override fun run() {
        val outputPath = output ?: "$target.delta"

        val sig =
            FileInputStream(signatureFile).use { input ->
                SignatureIO.read(input)
            }

        val algo = RsyncDelta()
        val delta =
            FileInputStream(target).use { input ->
                algo.makeDelta(input, sig)
            }

        FileOutputStream(outputPath).use { out ->
            DeltaIO.write(delta, out)
        }

        echo("Delta created: $outputPath (blockSize=${delta.blockSize}, ops=${delta.ops.size})")
    }
}
