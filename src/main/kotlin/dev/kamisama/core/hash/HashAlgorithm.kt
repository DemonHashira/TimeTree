package dev.kamisama.core.hash

/**
 * Interface for cryptographic hash algorithms.
 */
interface HashAlgorithm {
    // Updates hash with new bytes.
    fun update(
        data: ByteArray,
        off: Int = 0,
        len: Int = data.size,
    )

    // Finalizes and returns digest.
    fun digest(): ObjectId

    companion object {
        // Hashes complete byte array.
        fun computeAll(bytes: ByteArray): ObjectId {
            val h = Sha1()
            h.update(bytes, 0, bytes.size)
            return h.digest()
        }
    }
}
