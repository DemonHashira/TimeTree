package dev.kamisama.core.delta

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

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

        "add beyond capacity wraps correctly" {
            val buffer = RingBuffer(3)
            buffer.add(1)
            buffer.add(2)
            buffer.add(3)
            buffer.get(0) shouldBe 1.toByte()

            buffer.add(4)
            buffer.size() shouldBe 3
            buffer.get(0) shouldBe 2.toByte()
            buffer.get(1) shouldBe 3.toByte()
            buffer.get(2) shouldBe 4.toByte()
        }

        "toByteArray returns correct contiguous view" {
            val buffer = RingBuffer(5)
            buffer.add(10)
            buffer.add(20)
            buffer.add(30)

            buffer.toByteArray() shouldBe byteArrayOf(10, 20, 30)
        }

        "toByteArray works after wrapping" {
            val buffer = RingBuffer(3)
            buffer.add(1)
            buffer.add(2)
            buffer.add(3)
            buffer.add(4)
            buffer.add(5)

            buffer.toByteArray() shouldBe byteArrayOf(3, 4, 5)
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

        "extensive wrapping maintains correctness" {
            val buffer = RingBuffer(4)

            for (i in 0 until 100) {
                buffer.add((i % 256).toByte())
            }

            buffer.size() shouldBe 4
            buffer.get(0) shouldBe 96.toByte()
            buffer.get(1) shouldBe 97.toByte()
            buffer.get(2) shouldBe 98.toByte()
            buffer.get(3) shouldBe 99.toByte()
        }
    })
