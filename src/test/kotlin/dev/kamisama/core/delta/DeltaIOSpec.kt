package dev.kamisama.core.delta

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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

        test("round trip delta with mixed operations") {
            val ops =
                listOf(
                    DeltaOp.Copy(0L, 8192),
                    DeltaOp.Insert("MODIFIED".toByteArray()),
                    DeltaOp.Copy(16384L, 4096),
                )
            val delta = Delta(8192, ops)
            val out = ByteArrayOutputStream()
            DeltaIO.write(delta, out)

            val read = DeltaIO.read(ByteArrayInputStream(out.toByteArray()))
            read.ops.size shouldBe 3
            read.ops[0] shouldBe DeltaOp.Copy(0L, 8192)
            (read.ops[1] as DeltaOp.Insert).data shouldBe "MODIFIED".toByteArray()
            read.ops[2] shouldBe DeltaOp.Copy(16384L, 4096)
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

        test("large valid delta serializes correctly") {
            val ops =
                (0 until 1000).map { it ->
                    if (it % 2 == 0) {
                        DeltaOp.Copy((it * 1024).toLong(), 1024)
                    } else {
                        DeltaOp.Insert(ByteArray(10) { (it + 48).toByte() })
                    }
                }
            val delta = Delta(8192, ops)
            val out = ByteArrayOutputStream()
            DeltaIO.write(delta, out)

            val read = DeltaIO.read(ByteArrayInputStream(out.toByteArray()))
            read.ops.size shouldBe 1000
            read.blockSize shouldBe delta.blockSize
        }
    })
