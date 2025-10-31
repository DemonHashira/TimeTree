package dev.kamisama.core.delta

import dev.kamisama.core.hash.ObjectId
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
        BinaryIO.writeU32(out, sig.blockSize)
        BinaryIO.writeU32(out, sig.blocks.size)

        for (block in sig.blocks) {
            BinaryIO.writeU32(out, block.index)
            BinaryIO.writeU32(out, block.weak)
            out.write(block.strong.toByteArray())
        }
    }

    /**
     * Read a signature from an input stream.
     */
    fun read(input: InputStream): Signature {
        val magic = BinaryIO.readBytes(input, 5)
        if (!magic.contentEquals(MAGIC)) {
            throw IllegalArgumentException("Invalid signature magic (expected TTSG v1)")
        }

        val blockSize = BinaryIO.readU32(input)
        val count = BinaryIO.readU32(input)

        val blocks = mutableListOf<BlockSignature>()
        for (i in 0 until count) {
            val index = BinaryIO.readU32(input)
            val weak = BinaryIO.readU32(input)
            val strongBytes = BinaryIO.readBytes(input, 20)
            val strong = ObjectId.from(strongBytes)
            blocks.add(BlockSignature(index, weak, strong))
        }

        return Signature(blockSize, blocks)
    }
}
