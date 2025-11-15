package dev.kamisama.core.delta

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

/** Tests for rsync delta algorithm. */
class RsyncDeltaSpec :
    FunSpec({
        val algo = RsyncDelta()

        test("empty files produce empty delta") {
            val sig = algo.makeSignature(ByteArrayInputStream(byteArrayOf()), 8192)
            val delta = algo.makeDelta(ByteArrayInputStream(byteArrayOf()), sig)

            sig.blocks.shouldBeEmpty()
            delta.ops.shouldBeEmpty()
        }

        test("identical files produce only copy operations") {
            val data = "Hello World!".repeat(1000).toByteArray()
            val tempFile = Files.createTempFile("test", ".bin")
            Files.write(tempFile, data)

            val sig = algo.makeSignature(ByteArrayInputStream(data), 128)
            val delta = algo.makeDelta(ByteArrayInputStream(data), sig)

            delta.ops.all { it is DeltaOp.Copy } shouldBe true

            val out = ByteArrayOutputStream()
            algo.applyDelta(tempFile, delta, out)
            out.toByteArray() shouldBe data

            Files.delete(tempFile)
        }

        test("delta round-trip preserves arbitrary data") {
            checkAll(
                Arb.list(Arb.byte(), 0..10000),
                Arb.int(64..4096),
            ) { data, blockSize ->
                val bytes = data.toByteArray()
                val tempFile = Files.createTempFile("basis", ".bin")
                try {
                    Files.write(tempFile, bytes)

                    val sig = algo.makeSignature(ByteArrayInputStream(bytes), blockSize)
                    val delta = algo.makeDelta(ByteArrayInputStream(bytes), sig)

                    val out = ByteArrayOutputStream()
                    algo.applyDelta(tempFile, delta, out)
                    out.toByteArray() shouldBe bytes
                } finally {
                    Files.delete(tempFile)
                }
            }
        }

        test("insertion in middle is detected") {
            val basis = "AAABBB".toByteArray()
            val target = "AAAXXXBBB".toByteArray()
            val tempFile = Files.createTempFile("basis", ".bin")
            Files.write(tempFile, basis)

            val sig = algo.makeSignature(ByteArrayInputStream(basis), 64)
            val delta = algo.makeDelta(ByteArrayInputStream(target), sig)

            val out = ByteArrayOutputStream()
            algo.applyDelta(tempFile, delta, out)
            out.toByteArray() shouldBe target

            Files.delete(tempFile)
        }

        test("partial last block is handled correctly") {
            val basis = "HELLO".toByteArray()
            val target = "HELLOWORLD".toByteArray()
            val tempFile = Files.createTempFile("basis", ".bin")
            Files.write(tempFile, basis)

            val sig = algo.makeSignature(ByteArrayInputStream(basis), 8192)
            val delta = algo.makeDelta(ByteArrayInputStream(target), sig)

            val out = ByteArrayOutputStream()
            algo.applyDelta(tempFile, delta, out)
            out.toByteArray() shouldBe target

            Files.delete(tempFile)
        }

        test("copy past EOF in basis throws error") {
            val basis = "SHORT".toByteArray()
            val tempFile = Files.createTempFile("basis", ".bin")
            Files.write(tempFile, basis)

            val badDelta =
                Delta(
                    blockSize = 8192,
                    ops = listOf(DeltaOp.Copy(0L, 100000)),
                )

            val out = ByteArrayOutputStream()
            shouldThrow<IllegalStateException> {
                algo.applyDelta(tempFile, badDelta, out)
            }

            Files.delete(tempFile)
        }

        test("minimum block size (64) works correctly") {
            val data = "A".repeat(200).toByteArray()
            val tempFile = Files.createTempFile("test", ".bin")
            Files.write(tempFile, data)

            val sig = algo.makeSignature(ByteArrayInputStream(data), 64)
            val delta = algo.makeDelta(ByteArrayInputStream(data), sig)

            val out = ByteArrayOutputStream()
            algo.applyDelta(tempFile, delta, out)
            out.toByteArray() shouldBe data

            Files.delete(tempFile)
        }

        test("maximum block size (1MB) works correctly") {
            val data = ByteArray(2 * 1024 * 1024) { (it % 256).toByte() }
            val tempFile = Files.createTempFile("test", ".bin")
            Files.write(tempFile, data)

            val sig = algo.makeSignature(ByteArrayInputStream(data), 1024 * 1024)
            val delta = algo.makeDelta(ByteArrayInputStream(data), sig)

            val out = ByteArrayOutputStream()
            algo.applyDelta(tempFile, delta, out)
            out.toByteArray() shouldBe data

            Files.delete(tempFile)
        }

        test("block size below minimum is rejected") {
            shouldThrow<IllegalArgumentException> {
                algo.makeSignature(ByteArrayInputStream(byteArrayOf()), 32)
            }
        }

        test("block size above maximum is rejected") {
            shouldThrow<IllegalArgumentException> {
                algo.makeSignature(ByteArrayInputStream(byteArrayOf()), 2_000_000)
            }
        }
    })
