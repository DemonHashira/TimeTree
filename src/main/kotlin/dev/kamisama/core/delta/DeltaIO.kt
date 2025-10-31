package dev.kamisama.core.delta

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Binary serialization for Delta files.
 */
object DeltaIO {
    private val MAGIC = byteArrayOf('T'.code.toByte(), 'T'.code.toByte(), 'D'.code.toByte(), 'L'.code.toByte(), 0x01)
    private const val TAG_INSERT: Byte = 0
    private const val TAG_COPY: Byte = 1

    private const val MAX_OPS = 10_000_000
    private const val MAX_INSERT_LENGTH = 100 * 1024 * 1024

    /**
     * Write a delta to an output stream.
     */
    fun write(
        delta: Delta,
        out: OutputStream,
    ) {
        out.write(MAGIC)
        writeU32(out, delta.blockSize)
        VarInt.write(out, delta.ops.size.toLong())

        for (op in delta.ops) {
            when (op) {
                is DeltaOp.Insert -> {
                    out.write(TAG_INSERT.toInt())
                    VarInt.write(out, op.data.size.toLong())
                    out.write(op.data)
                }

                is DeltaOp.Copy -> {
                    out.write(TAG_COPY.toInt())
                    VarInt.write(out, op.offset)
                    VarInt.write(out, op.length.toLong())
                }
            }
        }
    }

    /**
     * Read a delta from an input stream.
     */
    fun read(input: InputStream): Delta {
        val magic = readBytes(input, 5)
        if (!magic.contentEquals(MAGIC)) {
            throw IllegalArgumentException("Invalid delta magic (expected TTDL v1)")
        }

        val blockSize = readU32(input)
        val opsCount = VarInt.read(input)

        if (opsCount !in 0..MAX_OPS) {
            throw IllegalArgumentException("Delta ops count out of bounds: $opsCount (max $MAX_OPS)")
        }

        val ops = mutableListOf<DeltaOp>()
        for (i in 0 until opsCount.toInt()) {
            val tag = input.read()
            if (tag < 0) {
                throw EOFException("Unexpected EOF while reading op tag")
            }

            when (tag.toByte()) {
                TAG_INSERT -> {
                    val len = VarInt.read(input)
                    if (len !in 0..MAX_INSERT_LENGTH) {
                        throw IllegalArgumentException("Insert length out of bounds: $len (max $MAX_INSERT_LENGTH)")
                    }
                    val data = readBytes(input, len.toInt())
                    ops.add(DeltaOp.Insert(data))
                }

                TAG_COPY -> {
                    val offset = VarInt.read(input)
                    val length = VarInt.read(input)
                    if (offset < 0) {
                        throw IllegalArgumentException("Copy offset cannot be negative: $offset")
                    }
                    if (length <= 0) {
                        throw IllegalArgumentException("Copy length must be positive: $length")
                    }
                    ops.add(DeltaOp.Copy(offset, length.toInt()))
                }

                else -> throw IllegalArgumentException("Unknown delta op tag: $tag")
            }
        }

        return Delta(blockSize, ops)
    }

    private fun writeU32(
        out: OutputStream,
        value: Int,
    ) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun readU32(input: InputStream): Int {
        val b0 = input.read()
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) {
            throw EOFException("Unexpected EOF while reading u32")
        }
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun readBytes(
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
