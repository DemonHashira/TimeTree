package dev.kamisama.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import dev.kamisama.core.hash.HashAlgorithm
import dev.kamisama.core.objects.ObjectHeaders
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Diagnostic command: prints the SHA-1 ids using
 * (a) git-style header and (b) TimeTree domain-separated header.
 */
class HashObjectCmd :
    CliktCommand(
        name = "hash-object",
    ) {
    private val path by argument("path")

    override fun help(context: Context) = "Compute object hash for a file"

    override fun run() {
        val p = Paths.get(path)
        require(Files.isRegularFile(p)) { "Not a regular file: $p" }
        val bytes = Files.readAllBytes(p)

        // a) Git-style: "blob <size>\\0" + content
        val gitHeader = "blob ${bytes.size}\u0000".toByteArray()
        val gitId = HashAlgorithm.computeAll(gitHeader + bytes).toHex()

        // b) TimeTree-style: "timetree:v1\\0blob <size>\\0" + content
        val ttHeader = ObjectHeaders.blobHeader(bytes.size.toLong())
        val ttId = HashAlgorithm.computeAll(ttHeader + bytes).toHex()

        echo("git-style: $gitId")
        echo("timetree: $ttId")
    }
}
