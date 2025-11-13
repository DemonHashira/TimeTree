package dev.kamisama.core.delta.io

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Base-128 variable-length integer encoding/decoding for compact delta representation.
 */
object VarInt {
    /**
     * Write a long value as a base-128 varint.
     */
    fun write(
        out: OutputStream,
        value: Long,
    ) {
        var v = value
        while (v >= 0x80) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write((v and 0x7F).toInt())
    }

    /**
     * Read a base-128 varint from the input stream.
     */
    fun read(input: InputStream): Long {
        var result = 0L
        var shift = 0
        var bytesRead = 0

        while (true) {
            if (bytesRead >= 10) {
                throw IllegalArgumentException("VarInt too long (>10 bytes)")
            }

            val b = input.read()
            if (b < 0) {
                throw EOFException("Unexpected EOF while reading varint")
            }

            result = result or ((b and 0x7F).toLong() shl shift)
            bytesRead++

            if ((b and 0x80) == 0) {
                break
            }

            shift += 7
        }

        return result
    }
}
