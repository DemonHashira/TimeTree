package dev.kamisama.core.fs

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.writeBytes

/**
 * Atomic file writes preventing corruption from partial writes.
 */
class AtomicFile(
    // The final destination path where the file should be written
    private val target: Path,
) {
    /** Writes UTF-8 text atomically. */
    fun writeUtf8(text: String) = write(text.toByteArray(StandardCharsets.UTF_8))

    /** Writes bytes atomically using temp file strategy. */
    fun write(bytes: ByteArray) {
        // Ensure the parent directory exists before creating a temporary file
        val dir = target.parent
        if (dir != null) Files.createDirectories(dir)

        // Create a temporary file with a unique name to avoid conflicts
        val tmp = dir.resolve(".tmp-${target.fileName}-${System.nanoTime()}")

        // Write content to a temporary file first
        tmp.writeBytes(bytes)

        Files.move(
            tmp,
            target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}
