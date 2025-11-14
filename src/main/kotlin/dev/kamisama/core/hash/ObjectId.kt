package dev.kamisama.core.hash

/**
 * Immutable 160-bit SHA-1 object identifier.
 */
class ObjectId private constructor(
    private val bytes: ByteArray,
) {
    init {
        require(bytes.size == 20) { "ObjectId must be 20 bytes (got ${bytes.size})" }
    }

    /** Returns copy of internal bytes. */
    fun toByteArray(): ByteArray = bytes.copyOf()

    /** Converts to lowercase hex string. */
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

        /** Creates ObjectId from a byte array. */
        fun from(bytes: ByteArray): ObjectId = ObjectId(bytes.copyOf())

        /** Creates ObjectId from a 40-character hex string. */
        fun fromHex(hex: String): ObjectId {
            require(hex.length == 40) { "SHA-1 hex must be 40 chars" }
            val out = ByteArray(20)
            var i = 0
            while (i < 40) {
                val hi = hex[i].digitToInt(16)
                val lo = hex[i + 1].digitToInt(16)
                out[i / 2] = ((hi shl 4) or lo).toByte()
                i += 2
            }
            return ObjectId(out)
        }
    }
}
