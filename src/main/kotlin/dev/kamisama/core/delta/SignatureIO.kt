package dev.kamisama.core.delta

import dev.kamisama.core.hash.ObjectId
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Binary serialization for Signature files.
 */
object SignatureIO {
    private val MAGIC = byteArrayOf('T'.code.toByte(), 'T'.code.toByte(), 'S'.code.toByte(), 'G'.code.toByte(), 0x01)

    /**
     * Write a signature to an output stream.
     */
    fun write(
        sig: Signature,
        out: OutputStream,
    ) {
        out.write(MAGIC)
        writeU32(out, sig.blockSize)
        writeU32(out, sig.blocks.size)

        for (block in sig.blocks) {
            writeU32(out, block.index)
            writeU32(out, block.weak)
            out.write(block.strong.toByteArray())
        }
    }

    /**
     * Read a signature from an input stream.
     */
    fun read(input: InputStream): Signature {
        val magic = readBytes(input, 5)
        if (!magic.contentEquals(MAGIC)) {
            throw IllegalArgumentException("Invalid signature magic (expected TTSG v1)")
        }

        val blockSize = readU32(input)
        val count = readU32(input)

        val blocks = mutableListOf<BlockSignature>()
        for (i in 0 until count) {
            val index = readU32(input)
            val weak = readU32(input)
            val strongBytes = readBytes(input, 20)
            val strong = ObjectId.from(strongBytes)
            blocks.add(BlockSignature(index, weak, strong))
        }

        return Signature(blockSize, blocks)
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
