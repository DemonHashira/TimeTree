package dev.kamisama.core.hash

/**
 * Hand-rolled SHA-1 (FIPS 180-4) with incremental update().
 * - 512-bit blocks, big-endian words
 * - 160-bit state (H0..H4)
 */
class Sha1 : Sha1Like {
    // State H0..H4 (32-bit words)
    private var h0 = 0x67452301
    private var h1 = 0xEFCDAB89.toInt()
    private var h2 = 0x98BADCFE.toInt()
    private var h3 = 0x10325476
    private var h4 = 0xC3D2E1F0.toInt()

    // Unprocessed bytes buffer (block = 64 bytes)
    private val block = ByteArray(64)
    private var blockLen = 0

    // Total length in bits (unsigned 64-bit)
    private var totalBits: Long = 0L

    override fun update(
        data: ByteArray,
        off: Int,
        len: Int,
    ) {
        if (len <= 0) return
        totalBits += (len.toLong() shl 3)

        var i = off
        var remaining = len

        // If the buffer has partial, fill it first
        if (blockLen > 0) {
            val need = 64 - blockLen
            val take = minOf(need, remaining)
            System.arraycopy(data, i, block, blockLen, take)
            blockLen += take
            i += take
            remaining -= take
            if (blockLen == 64) {
                processBlock(block, 0)
                blockLen = 0
            }
        }

        // Process full blocks directly from input
        while (remaining >= 64) {
            processBlock(data, i)
            i += 64
            remaining -= 64
        }

        // Buffer the rest
        if (remaining > 0) {
            System.arraycopy(data, i, block, 0, remaining)
            blockLen = remaining
        }
    }

    override fun digest(): ObjectId {
        // Pad: 0x80, zeros, 64-bit big-end length
        // Start with a copy of the current buffer
        val padLen = paddingLength(blockLen)
        val pad = ByteArray(padLen)
        pad[0] = 0x80.toByte()

        // The last 8 bytes are the length in bits big-endian
        val lenPos = padLen - 8
        writeLongBE(totalBits, pad, lenPos)

        update(pad, 0, pad.size) // this will flush final blocks

        // Produce digest (big-endian words)
        val out = ByteArray(20)
        writeIntBE(h0, out, 0)
        writeIntBE(h1, out, 4)
        writeIntBE(h2, out, 8)
        writeIntBE(h3, out, 12)
        writeIntBE(h4, out, 16)

        // Reset to prevent re-use bugs
        reset()

        return ObjectId.from(out)
    }

    private fun reset() {
        h0 = 0x67452301
        h1 = 0xEFCDAB89.toInt()
        h2 = 0x98BADCFE.toInt()
        h3 = 0x10325476
        h4 = 0xC3D2E1F0.toInt()
        blockLen = 0
        totalBits = 0L
    }

    private fun paddingLength(currLen: Int): Int {
        // We need to append 1 byte (0x80), then zeros, then 8 length bytes,
        // making the total% 64 == 0.
        val base = currLen + 1 + 8
        val rem = base % 64
        return if (rem == 0) (1 + 8) else (1 + (64 - rem) + 8)
    }

    private fun processBlock(
        buf: ByteArray,
        off: Int,
    ) {
        // Prepare message schedule W[0..79] as 32-bit ints
        val w = IntArray(80)
        var j = 0
        // The first 16 words come directly from the block (big-endian)
        while (j < 16) {
            val i = off + j * 4
            w[j] = ((buf[i].toInt() and 0xFF) shl 24) or
                ((buf[i + 1].toInt() and 0xFF) shl 16) or
                ((buf[i + 2].toInt() and 0xFF) shl 8) or
                (buf[i + 3].toInt() and 0xFF)
            j++
        }
        // Remaining words W[t] = (W[t-3] xor W[t-8] xor W[t-14] xor W[t-16]) <<< 1
        while (j < 80) {
            w[j] = rol1(w[j - 3] xor w[j - 8] xor w[j - 14] xor w[j - 16])
            j++
        }

        // Initialize working vars
        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4

        for (t in 0 until 80) {
            val (f, k) =
                when (t) {
                    in 0..19 -> ((b and c) or ((b.inv()) and d)) to 0x5A827999
                    in 20..39 -> (b xor c xor d) to 0x6ED9EBA1
                    in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                    else -> (b xor c xor d) to 0xCA62C1D6.toInt()
                }
            val temp = rol5(a) + f + e + k + w[t]
            e = d
            d = c
            c = rol30(b)
            b = a
            a = temp
        }

        // Add this chunk’s hash to the result so far:
        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
    }

    private fun rol1(x: Int): Int = (x shl 1) or (x ushr 31)

    private fun rol5(x: Int): Int = (x shl 5) or (x ushr 27)

    private fun rol30(x: Int): Int = (x ushr 2) or (x shl 30)

    private fun writeIntBE(
        v: Int,
        dst: ByteArray,
        off: Int,
    ) {
        dst[off] = (v ushr 24).toByte()
        dst[off + 1] = (v ushr 16).toByte()
        dst[off + 2] = (v ushr 8).toByte()
        dst[off + 3] = v.toByte()
    }

    private fun writeLongBE(
        v: Long,
        dst: ByteArray,
        off: Int,
    ) {
        dst[off] = (v ushr 56).toByte()
        dst[off + 1] = (v ushr 48).toByte()
        dst[off + 2] = (v ushr 40).toByte()
        dst[off + 3] = (v ushr 32).toByte()
        dst[off + 4] = (v ushr 24).toByte()
        dst[off + 5] = (v ushr 16).toByte()
        dst[off + 6] = (v ushr 8).toByte()
        dst[off + 7] = v.toByte()
    }
}
