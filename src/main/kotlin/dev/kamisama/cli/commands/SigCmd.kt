package dev.kamisama.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import dev.kamisama.cli.Color
import dev.kamisama.core.delta.RsyncDelta
import dev.kamisama.core.delta.io.SignatureIO
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Paths

/**
 * Generate a signature file from a basis file.
 */
class SigCmd : CliktCommand(name = "sig") {
    private val basis by argument()
    private val output by option("-o", "--output")
    private val blockSize by option("-b", "--block-size").int().default(8192)

    override fun help(context: Context) = "Generate a signature for a basis file"

    override fun run() {
        if (blockSize < 64 || blockSize > 1024 * 1024) {
            echo("${Color.red("Error:")} Block size must be between 64 and 1048576 (got $blockSize)", err = true)
            throw ProgramResult(1)
        }

        val basisPath = Paths.get(basis)
        val outputPath = output ?: "$basis.sig"

        val algo = RsyncDelta()
        val sig =
            FileInputStream(basisPath.toFile()).use { input ->
                algo.makeSignature(input, blockSize)
            }

        FileOutputStream(outputPath).use { out ->
            SignatureIO.write(sig, out)
        }

        echo("Signature created: $outputPath (blockSize=$blockSize, blocks=${sig.blocks.size})")
    }
}
