package dev.kamisama.core.fs

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.writeBytes

class AtomicFile(
    private val target: Path,
) {
    fun writeUtf8(text: String) = write(text.toByteArray(StandardCharsets.UTF_8))

    fun write(bytes: ByteArray) {
        val dir = target.parent
        if (dir != null) Files.createDirectories(dir)
        val tmp = dir.resolve(".tmp-${target.fileName}-${System.nanoTime()}")
        tmp.writeBytes(bytes)
        Files.move(
            tmp,
            target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}
