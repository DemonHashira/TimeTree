package dev.kamisama.core.delta

import java.util.ArrayDeque

/**
 * Tridgell/rsync-style rolling checksum using Adler-like sums modulo 2^16.
 */
class RsyncRoller(
    private val blockSize: Int,
) {
    private var a: Int = 0
    private var b: Int = 0
    private val window = ArrayDeque<Byte>(blockSize)

    /**
     * Initialize the rolling checksum with the first block.
     */
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

    /**
     * Roll the window by one byte: remove oldest, add newest.
     */
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

    /**
     * Get the current weak checksum as a 32-bit integer.
     */
    fun weak(): Int = (b shl 16) or a

    /**
     * Get the current window size.
     */
    fun size(): Int = window.size
}
