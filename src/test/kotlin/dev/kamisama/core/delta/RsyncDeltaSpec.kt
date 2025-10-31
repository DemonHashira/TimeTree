package dev.kamisama.core.delta

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

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

            // All ops should be Copy operations (multiple blocks matched)
            delta.ops.all { it is DeltaOp.Copy } shouldBe true

            val out = ByteArrayOutputStream()
            algo.applyDelta(tempFile, delta, out)
            out.toByteArray() shouldBe data

            Files.delete(tempFile)
        }

        test("append-only change produces small delta") {
            val basis = "A".repeat(10000).toByteArray()
            val target = (basis.decodeToString() + "NEW").toByteArray()
            val tempFile = Files.createTempFile("basis", ".bin")
            Files.write(tempFile, basis)

            val sig = algo.makeSignature(ByteArrayInputStream(basis), 1024)
            val delta = algo.makeDelta(ByteArrayInputStream(target), sig)

            delta.ops.filterIsInstance<DeltaOp.Insert>() shouldNotBe emptyList<DeltaOp>()
            delta.ops.filterIsInstance<DeltaOp.Copy>() shouldNotBe emptyList<DeltaOp>()

            val out = ByteArrayOutputStream()
            algo.applyDelta(tempFile, delta, out)
            out.toByteArray() shouldBe target

            Files.delete(tempFile)
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

        test("scattered small edits work correctly") {
            val basis = ByteArray(1000) { (it % 256).toByte() }
            val target = basis.copyOf()
            target[100] = 0xFF.toByte()
            target[500] = 0xAA.toByte()
            target[900] = 0x55.toByte()

            val tempFile = Files.createTempFile("basis", ".bin")
            Files.write(tempFile, basis)

            val sig = algo.makeSignature(ByteArrayInputStream(basis), 128)
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

        test("empty basis with large target streams properly") {
            val tempFile = Files.createTempFile("empty", ".bin")
            Files.write(tempFile, byteArrayOf())

            val target = ByteArray(200_000) { (it % 256).toByte() }

            val sig = algo.makeSignature(ByteArrayInputStream(byteArrayOf()), 8192)
            val delta = algo.makeDelta(ByteArrayInputStream(target), sig)

            val insertOps = delta.ops.filterIsInstance<DeltaOp.Insert>()
            (insertOps.size > 1) shouldBe true

            val out = ByteArrayOutputStream()
            algo.applyDelta(tempFile, delta, out)
            out.toByteArray() shouldBe target

            Files.delete(tempFile)
        }

        test("large file with small change produces efficient delta") {
            val basis = ByteArray(1024 * 1024) { (it % 256).toByte() }
            val target = basis.copyOf()
            target[512 * 1024] = 0xFF.toByte()

            val tempFile = Files.createTempFile("basis", ".bin")
            Files.write(tempFile, basis)

            val sig = algo.makeSignature(ByteArrayInputStream(basis), 8192)
            val delta = algo.makeDelta(ByteArrayInputStream(target), sig)

            val deltaSize =
                delta.ops.sumOf {
                    when (it) {
                        is DeltaOp.Insert -> it.data.size
                        is DeltaOp.Copy -> 16
                    }
                }
            (deltaSize < basis.size / 10) shouldBe true

            val out = ByteArrayOutputStream()
            algo.applyDelta(tempFile, delta, out)
            out.toByteArray() shouldBe target

            Files.delete(tempFile)
        }

        test("operations are coalesced correctly") {
            val basis = "A".repeat(10000).toByteArray()
            val tempFile = Files.createTempFile("basis", ".bin")
            Files.write(tempFile, basis)

            val sig = algo.makeSignature(ByteArrayInputStream(basis), 512)
            val delta = algo.makeDelta(ByteArrayInputStream(basis), sig)

            // All ops should be Copy operations
            delta.ops.all { it is DeltaOp.Copy } shouldBe true

            // Verify total copy length equals basis size
            val totalCopied = delta.ops.filterIsInstance<DeltaOp.Copy>().sumOf { it.length }
            totalCopied shouldBe basis.size

            Files.delete(tempFile)
        }

        test("copy past EOF in basis throws error") {
            val basis = "SHORT".toByteArray()
            val tempFile = Files.createTempFile("basis", ".bin")
            Files.write(tempFile, basis)

            // Create a malicious delta that tries to copy beyond the file end
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
