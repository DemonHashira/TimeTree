package dev.kamisama.core.hash

import java.io.InputStream

/**
 * Minimal streaming hash interface for computing SHA-1 digests.
 */
interface Sha1Like {
    /**
     * Updates the hash with new data bytes.
     */
    fun update(
        data: ByteArray,
        off: Int = 0,
        len: Int = data.size,
    )

    /**
     * Finalizes the hash and returns the computed ObjectId.
     */
    fun digest(): ObjectId

    companion object {
        /**
         * Convenience method to hash a complete byte array at once.
         */
        fun computeAll(bytes: ByteArray): ObjectId {
            val h = Sha1()
            h.update(bytes, 0, bytes.size)
            return h.digest()
        }

        /**
         * Streams data from InputStream and computes hash incrementally.
         */
        fun computeStream(
            src: InputStream,
            bufSize: Int = 8192,
        ): ObjectId {
            val h = Sha1()
            val buf = ByteArray(bufSize)
            while (true) {
                val n = src.read(buf)
                if (n <= 0) break // End of stream
                h.update(buf, 0, n)
            }
            return h.digest()
        }
    }
}
