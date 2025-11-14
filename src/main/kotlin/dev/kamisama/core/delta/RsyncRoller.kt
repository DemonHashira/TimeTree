package dev.kamisama.core.delta

import java.util.ArrayDeque

/**
 * Adler-style rolling checksum for rsync delta encoding.
 */
class RsyncRoller(
    private val blockSize: Int,
) {
    private var a: Int = 0
    private var b: Int = 0
    private val window = ArrayDeque<Byte>(blockSize)

    /** Initializes checksum with the first block of data. */
    fun init(
        block: ByteArray,
        off: Int,
        len: Int,
    ) {
        a = 0
        b = 0
        window.clear()

        for (i in 0 until len) {
            val byte = block[off + i].toInt() and 0xFF
            window.add(block[off + i])
            a = (a + byte) and 0xFFFF
            b = (b + a) and 0xFFFF
        }
    }

    /** Updates checksum by rolling window one byte forward. */
    fun roll(inByte: Byte) {
        val inVal = inByte.toInt() and 0xFF

        if (window.size >= blockSize) {
            val outByte = window.removeFirst()
            val outVal = outByte.toInt() and 0xFF

            a = (a - outVal + inVal) and 0xFFFF
            b = (b - (blockSize * outVal) + a) and 0xFFFF
        } else {
            a = (a + inVal) and 0xFFFF
            b = (b + a) and 0xFFFF
        }

        window.addLast(inByte)
    }

    /** Returns current weak checksum. */
    fun weak(): Int = (b shl 16) or a

    fun size(): Int = window.size
}
