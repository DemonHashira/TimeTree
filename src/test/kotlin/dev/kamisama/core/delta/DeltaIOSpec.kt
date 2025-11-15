package dev.kamisama.core.delta

import dev.kamisama.core.delta.io.DeltaIO
import dev.kamisama.core.delta.io.VarInt
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Tests for delta serialization. */
class DeltaIOSpec :
    FunSpec({

        test("round trip empty delta") {
            val delta = Delta(8192, emptyList())
            val out = ByteArrayOutputStream()
            DeltaIO.write(delta, out)

            val read = DeltaIO.read(ByteArrayInputStream(out.toByteArray()))
            read.blockSize shouldBe delta.blockSize
            read.ops.size shouldBe 0
        }

        test("round trip delta with insert operations") {
            val ops =
                listOf(
                    DeltaOp.Insert("Hello".toByteArray()),
                    DeltaOp.Insert("World".toByteArray()),
                )
            val delta = Delta(4096, ops)
            val out = ByteArrayOutputStream()
            DeltaIO.write(delta, out)

            val read = DeltaIO.read(ByteArrayInputStream(out.toByteArray()))
            read.blockSize shouldBe delta.blockSize
            read.ops.size shouldBe 2
            (read.ops[0] as DeltaOp.Insert).data shouldBe "Hello".toByteArray()
            (read.ops[1] as DeltaOp.Insert).data shouldBe "World".toByteArray()
        }

        test("round trip delta with copy operations") {
            val ops =
                listOf(
                    DeltaOp.Copy(0L, 1024),
                    DeltaOp.Copy(2048L, 512),
                )
            val delta = Delta(8192, ops)
            val out = ByteArrayOutputStream()
            DeltaIO.write(delta, out)

            val read = DeltaIO.read(ByteArrayInputStream(out.toByteArray()))
            read.blockSize shouldBe delta.blockSize
            read.ops.size shouldBe 2
            (read.ops[0] as DeltaOp.Copy).offset shouldBe 0L
            (read.ops[0] as DeltaOp.Copy).length shouldBe 1024
            (read.ops[1] as DeltaOp.Copy).offset shouldBe 2048L
            (read.ops[1] as DeltaOp.Copy).length shouldBe 512
        }

        test("round trip arbitrary deltas") {
            val insertOpArb = Arb.byteArray(Arb.int(0..1000), Arb.byte()).map { DeltaOp.Insert(it) }
            val copyOpArb =
                Arb.bind(
                    Arb.long(0L..1000000L),
                    Arb.int(1..10000),
                ) { offset, length -> DeltaOp.Copy(offset, length) }

            val deltaOpArb = Arb.choice(insertOpArb, copyOpArb)
            val deltaArb =
                Arb.bind(
                    Arb.int(64..8192),
                    Arb.list(deltaOpArb, 0..100),
                ) { blockSize, ops -> Delta(blockSize, ops) }

            checkAll(deltaArb) { delta ->
                val out = ByteArrayOutputStream()
                DeltaIO.write(delta, out)

                val read = DeltaIO.read(ByteArrayInputStream(out.toByteArray()))
                read.blockSize shouldBe delta.blockSize
                read.ops.size shouldBe delta.ops.size

                read.ops.zip(delta.ops).forEach { (readOp, originalOp) ->
                    when {
                        readOp is DeltaOp.Insert && originalOp is DeltaOp.Insert ->
                            readOp.data shouldBe originalOp.data

                        readOp is DeltaOp.Copy && originalOp is DeltaOp.Copy -> {
                            readOp.offset shouldBe originalOp.offset
                            readOp.length shouldBe originalOp.length
                        }
                    }
                }
            }
        }

        test("invalid magic is rejected") {
            val badData = "BADMAGIC".toByteArray()
            shouldThrow<IllegalArgumentException> {
                DeltaIO.read(ByteArrayInputStream(badData))
            }
        }

        test("excessive ops count is rejected") {
            val out = ByteArrayOutputStream()
            out.write(byteArrayOf('T'.code.toByte(), 'T'.code.toByte(), 'D'.code.toByte(), 'L'.code.toByte(), 0x01))
            out.write(byteArrayOf(0, 0, 0x20, 0))
            VarInt.write(out, 20_000_000L)

            shouldThrow<IllegalArgumentException> {
                DeltaIO.read(ByteArrayInputStream(out.toByteArray()))
            }
        }

        test("excessive insert length is rejected") {
            val out = ByteArrayOutputStream()
            out.write(byteArrayOf('T'.code.toByte(), 'T'.code.toByte(), 'D'.code.toByte(), 'L'.code.toByte(), 0x01))
            out.write(byteArrayOf(0, 0, 0x20, 0))
            VarInt.write(out, 1L)
            out.write(0)
            VarInt.write(out, 200_000_000L)

            shouldThrow<IllegalArgumentException> {
                DeltaIO.read(ByteArrayInputStream(out.toByteArray()))
            }
        }

        test("unknown op tag is rejected") {
            val out = ByteArrayOutputStream()
            out.write(byteArrayOf('T'.code.toByte(), 'T'.code.toByte(), 'D'.code.toByte(), 'L'.code.toByte(), 0x01))
            out.write(byteArrayOf(0, 0, 0x20, 0))
            VarInt.write(out, 1L)
            out.write(99)

            shouldThrow<IllegalArgumentException> {
                DeltaIO.read(ByteArrayInputStream(out.toByteArray()))
            }
        }
    })
