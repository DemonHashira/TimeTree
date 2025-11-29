package dev.kamisama.cli.commands

import com.github.ajalt.clikt.testing.test
import dev.kamisama.core.delta.io.DeltaIO
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.io.FileInputStream
import java.nio.file.Files

/**
 * Tests for delta command.
 */
class DeltaCmdTest :
    StringSpec({

        "delta should create delta file from signature and target" {
            val tmp = tempdir().toPath()

            val basisFile = tmp.resolve("basis.txt")
            val targetFile = tmp.resolve("target.txt")
            Files.writeString(basisFile, "Hello World")
            Files.writeString(targetFile, "Hello Kotlin World")

            val sigFile = tmp.resolve("basis.txt.sig")
            val sigCmd = SigCmd()
            val sigResult = sigCmd.test(argv = arrayOf(basisFile.toString(), "-b", "64", "-o", sigFile.toString()))
            sigResult.statusCode shouldBe 0
            Files.exists(sigFile) shouldBe true

            val deltaCmd = DeltaCmd()
            val result = deltaCmd.test(argv = arrayOf(sigFile.toString(), targetFile.toString()))
            result.statusCode shouldBe 0
            result.stdout shouldContain "Delta created:"
            result.stdout shouldContain "target.txt.delta"

            val deltaFile = tmp.resolve("target.txt.delta")
            Files.exists(deltaFile) shouldBe true
        }

        "delta should use custom output path when specified" {
            val tmp = tempdir().toPath()

            val basisFile = tmp.resolve("old.bin")
            val targetFile = tmp.resolve("new.bin")
            Files.write(basisFile, ByteArray(100) { it.toByte() })
            Files.write(targetFile, ByteArray(100) { (it + 1).toByte() })

            val sigFile = tmp.resolve("old.bin.sig")
            val sigCmd = SigCmd()
            sigCmd.test(argv = arrayOf(basisFile.toString(), "-o", sigFile.toString()))

            val customDelta = tmp.resolve("custom.delta")
            val deltaCmd = DeltaCmd()
            val result =
                deltaCmd.test(argv = arrayOf(sigFile.toString(), targetFile.toString(), "-o", customDelta.toString()))
            result.statusCode shouldBe 0
            result.stdout shouldContain customDelta.toString()

            Files.exists(customDelta) shouldBe true
        }

        "delta should handle identical basis and target" {
            val tmp = tempdir().toPath()

            val basisFile = tmp.resolve("same.txt")
            val targetFile = tmp.resolve("same_copy.txt")
            val content = "This content is identical in both files"
            Files.writeString(basisFile, content)
            Files.writeString(targetFile, content)

            val sigFile = tmp.resolve("same.txt.sig")
            SigCmd().test(argv = arrayOf(basisFile.toString(), "-o", sigFile.toString()))

            val deltaFile = tmp.resolve("same_copy.txt.delta")
            val result =
                DeltaCmd().test(argv = arrayOf(sigFile.toString(), targetFile.toString(), "-o", deltaFile.toString()))
            result.statusCode shouldBe 0

            Files.exists(deltaFile) shouldBe true
        }

        "delta should handle empty target file" {
            val tmp = tempdir().toPath()

            val basisFile = tmp.resolve("nonempty.txt")
            val targetFile = tmp.resolve("empty.txt")
            Files.writeString(basisFile, "Some content here")
            Files.writeString(targetFile, "")

            val sigFile = tmp.resolve("nonempty.txt.sig")
            SigCmd().test(argv = arrayOf(basisFile.toString(), "-o", sigFile.toString()))

            val deltaFile = tmp.resolve("empty.txt.delta")
            val result =
                DeltaCmd().test(argv = arrayOf(sigFile.toString(), targetFile.toString(), "-o", deltaFile.toString()))
            result.statusCode shouldBe 0

            Files.exists(deltaFile) shouldBe true
        }

        "delta should preserve blockSize from signature" {
            val tmp = tempdir().toPath()

            val basisFile = tmp.resolve("test.dat")
            val targetFile = tmp.resolve("test_modified.dat")
            Files.write(basisFile, ByteArray(1000) { it.toByte() })
            Files.write(targetFile, ByteArray(1000) { (it * 2).toByte() })

            val customBlockSize = 256

            val sigFile = tmp.resolve("test.dat.sig")
            SigCmd().test(
                argv =
                    arrayOf(
                        basisFile.toString(),
                        "-b",
                        customBlockSize.toString(),
                        "-o",
                        sigFile.toString(),
                    ),
            )

            val deltaFile = tmp.resolve("test_modified.dat.delta")
            DeltaCmd().test(argv = arrayOf(sigFile.toString(), targetFile.toString(), "-o", deltaFile.toString()))

            val delta = FileInputStream(deltaFile.toFile()).use { DeltaIO.read(it) }
            delta.blockSize shouldBe customBlockSize
        }

        "delta should generate valid deltas for arbitrary text modifications" {
            checkAll(10, Arb.string(0..2000), Arb.string(0..2000)) { basisContent, targetContent ->
                val tmp = tempdir().toPath()
                val basisFile = tmp.resolve("basis.txt")
                val targetFile = tmp.resolve("target.txt")
                Files.writeString(basisFile, basisContent)
                Files.writeString(targetFile, targetContent)

                val sigFile = tmp.resolve("basis.txt.sig")
                SigCmd().test(argv = arrayOf(basisFile.toString(), "-o", sigFile.toString()))

                val deltaFile = tmp.resolve("target.txt.delta")
                val result =
                    DeltaCmd().test(
                        argv =
                            arrayOf(
                                sigFile.toString(),
                                targetFile.toString(),
                                "-o",
                                deltaFile.toString(),
                            ),
                    )

                result.statusCode shouldBe 0
                Files.exists(deltaFile) shouldBe true

                val delta = FileInputStream(deltaFile.toFile()).use { DeltaIO.read(it) }
                delta.blockSize shouldBe 8192
            }
        }

        "delta should generate valid deltas for arbitrary binary modifications" {
            checkAll(10, Arb.list(Arb.byte(), 0..3000), Arb.list(Arb.byte(), 0..3000)) { basisData, targetData ->
                val tmp = tempdir().toPath()
                val basisFile = tmp.resolve("basis.bin")
                val targetFile = tmp.resolve("target.bin")
                Files.write(basisFile, basisData.toByteArray())
                Files.write(targetFile, targetData.toByteArray())

                val sigFile = tmp.resolve("basis.bin.sig")
                SigCmd().test(argv = arrayOf(basisFile.toString(), "-o", sigFile.toString()))

                val deltaFile = tmp.resolve("target.bin.delta")
                val result =
                    DeltaCmd().test(
                        argv =
                            arrayOf(
                                sigFile.toString(),
                                targetFile.toString(),
                                "-o",
                                deltaFile.toString(),
                            ),
                    )

                result.statusCode shouldBe 0
                Files.exists(deltaFile) shouldBe true
            }
        }
    })
