package dev.kamisama.core.delta

/**
 * Circular ring buffer for O(1) sliding window operations.
 * Avoids per-byte array shifts in the hot path.
 */
class RingBuffer(
    private val capacity: Int,
) {
    private val data = ByteArray(capacity)
    private var head = 0
    private var size = 0

    fun add(byte: Byte) {
        if (size < capacity) {
            data[(head + size) % capacity] = byte
            size++
        } else {
            data[head] = byte
            head = (head + 1) % capacity
        }
    }

    fun add(value: Int) {
        add((value and 0xFF).toByte())
    }

    fun get(index: Int): Byte {
        require(index in 0..<size) { "Index out of bounds: $index" }
        return data[(head + index) % capacity]
    }

    fun size(): Int = size

    fun isEmpty(): Boolean = size == 0

    fun clear() {
        size = 0
        head = 0
    }

    /**
     * Materialize a contiguous view of the ring buffer contents.
     * Only used when computing strong checksums on match candidates.
     */
    fun toByteArray(): ByteArray {
        val result = ByteArray(size)
        for (i in 0 until size) {
            result[i] = get(i)
        }
        return result
    }
}
