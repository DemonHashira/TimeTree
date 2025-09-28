package dev.kamisama.core.fs

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.writeBytes

/**
 * Utility class for performing atomic file writes.
 * Ensures file writes are either completely successful or leave the target unchanged,
 * preventing corruption from partial writes due to crashes or interruptions.
 */
class AtomicFile(
    // The final destination path where the file should be written
    private val target: Path,
) {
    /**
     * Atomically writes UTF-8 encoded text to the target file.
     * @param text The string content to write
     */
    fun writeUtf8(text: String) = write(text.toByteArray(StandardCharsets.UTF_8))

    /**
     * Atomically writes raw bytes to the target file.
     * Uses write-then-move strategy: writes to temporary file first,
     * then atomically moves it to replace the target.
     * @param bytes The byte array to write
     */
    fun write(bytes: ByteArray) {
        // Ensure the parent directory exists before creating a temporary file
        val dir = target.parent
        if (dir != null) Files.createDirectories(dir)

        // Create a temporary file with a unique name to avoid conflicts
        // Uses nanosecond timestamp to ensure uniqueness
        val tmp = dir.resolve(".tmp-${target.fileName}-${System.nanoTime()}")

        // Write content to a temporary file first
        tmp.writeBytes(bytes)

        // Atomically move a temporary file to the target location
        // ATOMIC_MOVE ensures operation is atomic on supporting filesystems
        // REPLACE_EXISTING allows overwriting an existing target file
        Files.move(
            tmp,
            target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}
