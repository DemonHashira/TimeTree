package dev.kamisama.core.hash

/**
 * Immutable 160-bit object id (SHA-1 digest = 20 bytes).
 */
class ObjectId private constructor(
    private val bytes: ByteArray,
) {
    init {
        require(bytes.size == 20) { "ObjectId must be 20 bytes (got ${bytes.size})" }
    }

    /**
     * Returns a copy of the internal byte array to maintain immutability.
     */
    fun toByteArray(): ByteArray = bytes.copyOf()

    /**
     * Converts the object ID to a lowercase hexadecimal string representation.
     */
    fun toHex(): String =
        buildString(bytes.size * 2) {
            for (b in bytes) {
                // Convert signed byte to unsigned int (0-255 range)
                val v = b.toInt() and 0xFF
                // Extract high 4 bits and low 4 bits for hex digits
                append(HEX[v ushr 4])
                append(HEX[v and 0x0F])
            }
        }

    override fun toString(): String = toHex()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ObjectId) return false
        // Use contentEquals for proper byte array comparison
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    companion object {
        // Hex character lookup table for efficient conversion
        private val HEX = "0123456789abcdef".toCharArray()

        /**
         * Creates an ObjectId from a byte array, copying the input to ensure immutability.
         */
        fun from(bytes: ByteArray): ObjectId = ObjectId(bytes.copyOf())

        /**
         * Creates an ObjectId from a 40-character hexadecimal string.
         */
        fun fromHex(hex: String): ObjectId {
            require(hex.length == 40) { "SHA-1 hex must be 40 chars" }
            val out = ByteArray(20)
            var i = 0
            while (i < 40) {
                // Convert pairs of hex characters to bytes
                val hi = hex[i].digitToInt(16) // High 4 bits
                val lo = hex[i + 1].digitToInt(16) // Low 4 bits
                out[i / 2] = ((hi shl 4) or lo).toByte()
                i += 2
            }
            return ObjectId(out)
        }
    }
}
