package dev.kamisama.core.delta.io

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Shared binary I/O utilities for delta and signature serialization.
 */
internal object BinaryIO {
    /**
     * Write a 32-bit unsigned integer in big-endian format.
     */
    fun writeU32(
        out: OutputStream,
        value: Int,
    ) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    /**
     * Read a 32-bit unsigned integer in big-endian format.
     */
    fun readU32(input: InputStream): Int {
        val b0 = input.read()
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) {
            throw EOFException("Unexpected EOF while reading u32")
        }
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    /**
     * Read exactly `count` bytes from input stream.
     */
    fun readBytes(
        input: InputStream,
        count: Int,
    ): ByteArray {
        val result = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val n = input.read(result, offset, count - offset)
            if (n < 0) {
                throw EOFException("Unexpected EOF while reading $count bytes")
            }
            offset += n
        }
        return result
    }
}
