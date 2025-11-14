package dev.kamisama.core.delta

/**
 * Ring buffer for efficient sliding window operations in delta encoding.
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

    /** Materializes ring buffer as a contiguous array for strong checksum. */
    fun toByteArray(): ByteArray {
        val result = ByteArray(size)
        for (i in 0 until size) {
            result[i] = get(i)
        }
        return result
    }
}
