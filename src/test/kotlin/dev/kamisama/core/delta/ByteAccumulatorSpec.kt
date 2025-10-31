package dev.kamisama.core.delta

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ByteAccumulatorSpec :
    StringSpec({

        "empty accumulator has size zero" {
            val acc = ByteAccumulator()
            acc.size() shouldBe 0
            acc.isEmpty() shouldBe true
        }

        "add single bytes" {
            val acc = ByteAccumulator()
            acc.add(10)
            acc.add(20)
            acc.add(30)

            acc.size() shouldBe 3
            acc.isEmpty() shouldBe false
            acc.toByteArray() shouldBe byteArrayOf(10, 20, 30)
        }

        "addAll appends byte array" {
            val acc = ByteAccumulator()
            acc.add(1)
            acc.addAll(byteArrayOf(2, 3, 4))
            acc.add(5)

            acc.toByteArray() shouldBe byteArrayOf(1, 2, 3, 4, 5)
        }

        "addAll with offset and length" {
            val acc = ByteAccumulator()
            val data = byteArrayOf(10, 20, 30, 40, 50)
            acc.addAll(data, 1, 3)

            acc.toByteArray() shouldBe byteArrayOf(20, 30, 40)
        }

        "clear resets accumulator" {
            val acc = ByteAccumulator()
            acc.add(1)
            acc.add(2)
            acc.add(3)

            acc.size() shouldBe 3

            acc.clear()
            acc.size() shouldBe 0
            acc.isEmpty() shouldBe true
            acc.toByteArray() shouldBe byteArrayOf()
        }

        "can reuse after clear" {
            val acc = ByteAccumulator()
            acc.add(1)
            acc.add(2)
            acc.clear()
            acc.add(3)
            acc.add(4)

            acc.toByteArray() shouldBe byteArrayOf(3, 4)
        }

        "handles large accumulation" {
            val acc = ByteAccumulator()
            for (i in 0 until 10000) {
                acc.add((i % 256).toByte())
            }

            acc.size() shouldBe 10000
            val result = acc.toByteArray()
            result.size shouldBe 10000
            result[0] shouldBe 0.toByte()
            result[255] shouldBe 255.toByte()
        }

        "initial capacity doesn't affect behavior" {
            val acc1 = ByteAccumulator(10)
            val acc2 = ByteAccumulator(1000)

            val data = ByteArray(500) { it.toByte() }
            acc1.addAll(data)
            acc2.addAll(data)

            acc1.toByteArray() shouldBe acc2.toByteArray()
        }
    })
