package dev.kamisama.core.delta

import java.io.ByteArrayOutputStream

/**
 * Efficient unboxed byte accumulator for building INSERT operations.
 * Avoids per-byte boxing and reduces GC pressure.
 */
class ByteAccumulator(
    initialCapacity: Int = 1024,
) {
    private val buffer = ByteArrayOutputStream(initialCapacity)

    fun add(byte: Byte) {
        buffer.write(byte.toInt())
    }

    fun add(value: Int) {
        buffer.write(value and 0xFF)
    }

    fun addAll(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size,
    ) {
        buffer.write(bytes, offset, length)
    }

    fun isEmpty(): Boolean = buffer.size() == 0

    fun size(): Int = buffer.size()

    fun toByteArray(): ByteArray = buffer.toByteArray()

    fun clear() {
        buffer.reset()
    }
}
