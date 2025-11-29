package dev.kamisama.core.hash

// Algorithm-agnostic object identifier supporting any hash function.
class ObjectId private constructor(
    private val bytes: ByteArray,
) {
    // Returns copy of internal bytes.
    fun toByteArray(): ByteArray = bytes.copyOf()

    // Size of this hash in bytes.
    val size: Int get() = bytes.size

    // Converts to lowercase hex string.
    fun toHex(): String =
        buildString(bytes.size * 2) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                append(HEX[v ushr 4])
                append(HEX[v and 0x0F])
            }
        }

    override fun toString(): String = toHex()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ObjectId) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    companion object {
        private val HEX = "0123456789abcdef".toCharArray()

        // Creates ObjectId from a byte array.
        fun from(bytes: ByteArray): ObjectId {
            require(bytes.isNotEmpty()) { "Hash bytes cannot be empty" }
            return ObjectId(bytes.copyOf())
        }

        // Creates ObjectId from a hex string.
        fun fromHex(hex: String): ObjectId {
            require(hex.isNotEmpty()) { "Hex string cannot be empty" }
            require(hex.length % 2 == 0) { "Hex string must have even length (got ${hex.length})" }

            val out = ByteArray(hex.length / 2)
            var i = 0
            while (i < hex.length) {
                val hi = hex[i].digitToInt(16)
                val lo = hex[i + 1].digitToInt(16)
                out[i / 2] = ((hi shl 4) or lo).toByte()
                i += 2
            }
            return ObjectId(out)
        }
    }
}
