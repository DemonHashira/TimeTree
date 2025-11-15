package dev.kamisama.core.delta

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/** Tests for byte accumulator buffer. */
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

        "accumulator correctly stores arbitrary byte sequences" {
            checkAll<List<Byte>>(Arb.list(Arb.byte(), 0..1000)) { bytes ->
                val acc = ByteAccumulator()
                bytes.forEach { acc.add(it) }

                acc.size() shouldBe bytes.size
                acc.toByteArray() shouldBe bytes.toByteArray()
            }
        }

        "addAll appends arbitrary byte arrays" {
            checkAll(Arb.byteArray(Arb.int(0..100), Arb.byte())) { bytes ->
                val acc = ByteAccumulator()
                acc.add(1)
                acc.addAll(bytes)
                acc.add(5)

                acc.toByteArray() shouldBe byteArrayOf(1) + bytes + byteArrayOf(5)
            }
        }

        "clear and reuse works correctly" {
            checkAll(Arb.list(Arb.byte(), 1..100), Arb.list(Arb.byte(), 1..100)) { bytes1, bytes2 ->
                val acc = ByteAccumulator()
                bytes1.forEach { acc.add(it) }
                acc.clear()
                bytes2.forEach { acc.add(it) }

                acc.toByteArray() shouldBe bytes2.toByteArray()
            }
        }

        "initial capacity doesn't affect behavior with arbitrary data" {
            checkAll(Arb.int(1..1000), Arb.byteArray(Arb.int(0..500), Arb.byte())) { capacity, data ->
                val acc1 = ByteAccumulator(capacity)
                val acc2 = ByteAccumulator(10)

                acc1.addAll(data)
                acc2.addAll(data)

                acc1.toByteArray() shouldBe acc2.toByteArray()
            }
        }
    })
