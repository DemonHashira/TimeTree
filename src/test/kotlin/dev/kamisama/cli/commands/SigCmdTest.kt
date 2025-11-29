package dev.kamisama.cli.commands

import com.github.ajalt.clikt.testing.test
import dev.kamisama.core.delta.io.SignatureIO
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.io.FileInputStream
import java.nio.file.Files

/**
 * Tests for signature creation.
 */
class SigCmdTest :
    StringSpec({

        "sig should generate signature file with default block size" {
            val tmp = tempdir().toPath()
            val basisFile = tmp.resolve("basis.txt")
            Files.writeString(basisFile, "Hello, this is the basis file content for testing.")

            val cmd = SigCmd()
            val result = cmd.test(argv = arrayOf(basisFile.toString()))
            result.statusCode shouldBe 0
            result.stdout shouldContain "Signature created:"
            result.stdout shouldContain "blockSize=8192"

            val sigFile = tmp.resolve("basis.txt.sig")
            Files.exists(sigFile) shouldBe true
        }

        "sig should use custom output path when specified" {
            val tmp = tempdir().toPath()
            val basisFile = tmp.resolve("data.bin")
            Files.write(basisFile, ByteArray(1024) { it.toByte() })

            val customOutput = tmp.resolve("custom.sig")
            val cmd = SigCmd()
            val result = cmd.test(argv = arrayOf(basisFile.toString(), "-o", customOutput.toString()))
            result.statusCode shouldBe 0
            result.stdout shouldContain customOutput.toString()

            Files.exists(customOutput) shouldBe true
        }

        "sig should reject block size below minimum (64)" {
            val tmp = tempdir().toPath()
            val basisFile = tmp.resolve("small.txt")
            Files.writeString(basisFile, "test content")

            val cmd = SigCmd()
            val result = cmd.test(argv = arrayOf(basisFile.toString(), "-b", "32"))
            result.statusCode shouldBe 1
            result.stderr shouldContain "Block size must be between 64 and 1048576"
        }

        "sig should reject block size above maximum (1048576)" {
            val tmp = tempdir().toPath()
            val basisFile = tmp.resolve("large.bin")
            Files.write(basisFile, ByteArray(100) { it.toByte() })

            val cmd = SigCmd()
            val result = cmd.test(argv = arrayOf(basisFile.toString(), "-b", "2097152"))
            result.statusCode shouldBe 1
            result.stderr shouldContain "Block size must be between 64 and 1048576"
        }

        "sig should handle empty files" {
            val tmp = tempdir().toPath()
            val emptyFile = tmp.resolve("empty.txt")
            Files.writeString(emptyFile, "")

            val cmd = SigCmd()
            val result = cmd.test(argv = arrayOf(emptyFile.toString()))
            result.statusCode shouldBe 0
            result.stdout shouldContain "blocks=0"

            val sigFile = tmp.resolve("empty.txt.sig")
            Files.exists(sigFile) shouldBe true
        }

        "sig should generate valid signatures for arbitrary text content" {
            checkAll(10, Arb.string(0..5000)) { content ->
                val tmp = tempdir().toPath()
                val basisFile = tmp.resolve("test.txt")
                Files.writeString(basisFile, content)

                val sigFile = tmp.resolve("test.txt.sig")
                val cmd = SigCmd()
                val result = cmd.test(argv = arrayOf(basisFile.toString(), "-o", sigFile.toString()))

                result.statusCode shouldBe 0
                Files.exists(sigFile) shouldBe true

                val sig = FileInputStream(sigFile.toFile()).use { SignatureIO.read(it) }
                sig.blockSize shouldBe 8192
                val expectedBlocks = (content.length + 8191) / 8192
                sig.blocks.size shouldBe expectedBlocks
            }
        }

        "sig should generate valid signatures for arbitrary binary data" {
            checkAll(10, Arb.list(Arb.byte(), 0..10000)) { data ->
                val tmp = tempdir().toPath()
                val basisFile = tmp.resolve("test.bin")
                Files.write(basisFile, data.toByteArray())

                val sigFile = tmp.resolve("test.bin.sig")
                val cmd = SigCmd()
                val result = cmd.test(argv = arrayOf(basisFile.toString(), "-o", sigFile.toString()))

                result.statusCode shouldBe 0
                Files.exists(sigFile) shouldBe true

                val sig = FileInputStream(sigFile.toFile()).use { SignatureIO.read(it) }
                sig.blockSize shouldBe 8192
            }
        }

        "sig should respect custom block sizes within valid range" {
            checkAll(10, Arb.int(64..1048576), Arb.list(Arb.byte(), 100..5000)) { blockSize, data ->
                val tmp = tempdir().toPath()
                val basisFile = tmp.resolve("test.bin")
                Files.write(basisFile, data.toByteArray())

                val sigFile = tmp.resolve("test.bin.sig")
                val cmd = SigCmd()
                val result =
                    cmd.test(argv = arrayOf(basisFile.toString(), "-b", blockSize.toString(), "-o", sigFile.toString()))

                result.statusCode shouldBe 0
                val sig = FileInputStream(sigFile.toFile()).use { SignatureIO.read(it) }
                sig.blockSize shouldBe blockSize

                val expectedBlocks = (data.size + blockSize - 1) / blockSize
                sig.blocks.size shouldBe expectedBlocks
            }
        }
    })
