package dev.kamisama.core.delta

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/** Tests for circular ring buffer. */
class RingBufferSpec :
    StringSpec({

        "empty buffer has size zero" {
            val buffer = RingBuffer(10)
            buffer.size() shouldBe 0
            buffer.isEmpty() shouldBe true
        }

        "add fills buffer up to capacity" {
            val buffer = RingBuffer(5)
            for (i in 0 until 5) {
                buffer.add(i.toByte())
            }
            buffer.size() shouldBe 5

            for (i in 0 until 5) {
                buffer.get(i) shouldBe i.toByte()
            }
        }

        "toByteArray maintains last N elements after arbitrary insertions" {
            checkAll<List<Byte>>(Arb.list(Arb.byte(), 1..100)) { bytes ->
                val capacity = (bytes.size / 2).coerceAtLeast(1)
                val buffer = RingBuffer(capacity)

                bytes.forEach { buffer.add(it) }

                val result = buffer.toByteArray()
                val expected = bytes.takeLast(capacity).toByteArray()
                result shouldBe expected
            }
        }

        "buffer maintains correct size after arbitrary operations" {
            checkAll(Arb.int(1..100), Arb.list(Arb.byte(), 0..200)) { capacity, bytes ->
                val buffer = RingBuffer(capacity)
                bytes.forEach { buffer.add(it) }
                buffer.size() shouldBe minOf(bytes.size, capacity)
            }
        }

        "clear resets buffer" {
            val buffer = RingBuffer(5)
            buffer.add(1)
            buffer.add(2)
            buffer.add(3)

            buffer.clear()
            buffer.size() shouldBe 0
            buffer.isEmpty() shouldBe true
        }

        "get with invalid index throws" {
            val buffer = RingBuffer(5)
            buffer.add(1)
            buffer.add(2)

            shouldThrow<IllegalArgumentException> { buffer.get(-1) }
            shouldThrow<IllegalArgumentException> { buffer.get(2) }
        }
    })
