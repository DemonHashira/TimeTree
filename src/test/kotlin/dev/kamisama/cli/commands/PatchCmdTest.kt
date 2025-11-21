package dev.kamisama.cli.commands

import com.github.ajalt.clikt.testing.test
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
import java.nio.file.Files

class PatchCmdTest :
    StringSpec({

        "patch should reconstruct target from basis and delta" {
            val tmp = tempdir().toPath()

            val basisFile = tmp.resolve("basis.txt")
            val targetFile = tmp.resolve("target.txt")
            val basisContent = "Hello World"
            val targetContent = "Hello Kotlin World"

            Files.writeString(basisFile, basisContent)
            Files.writeString(targetFile, targetContent)

            val sigFile = tmp.resolve("basis.txt.sig")
            SigCmd().test(argv = arrayOf(basisFile.toString(), "-o", sigFile.toString()))

            val deltaFile = tmp.resolve("target.txt.delta")
            DeltaCmd().test(argv = arrayOf(sigFile.toString(), targetFile.toString(), "-o", deltaFile.toString()))

            val reconstructed = tmp.resolve("reconstructed.bin")
            val result =
                PatchCmd().test(
                    argv =
                        arrayOf(
                            basisFile.toString(),
                            deltaFile.toString(),
                            "-o",
                            reconstructed.toString(),
                        ),
                )
            result.statusCode shouldBe 0
            result.stdout shouldContain "Reconstructed file:"

            Files.exists(reconstructed) shouldBe true
            Files.readString(reconstructed) shouldBe targetContent
        }

        "patch should use custom output path when specified" {
            val tmp = tempdir().toPath()

            val basisFile = tmp.resolve("original.txt")
            val targetFile = tmp.resolve("modified.txt")
            Files.writeString(basisFile, "ABCDEFGH")
            Files.writeString(targetFile, "ABXYEFGH")

            val sigFile = tmp.resolve("original.txt.sig")
            SigCmd().test(argv = arrayOf(basisFile.toString(), "-o", sigFile.toString()))

            val deltaFile = tmp.resolve("modified.txt.delta")
            DeltaCmd().test(argv = arrayOf(sigFile.toString(), targetFile.toString(), "-o", deltaFile.toString()))

            val customOutput = tmp.resolve("custom_reconstructed.txt")
            val result =
                PatchCmd().test(
                    argv =
                        arrayOf(
                            basisFile.toString(),
                            deltaFile.toString(),
                            "-o",
                            customOutput.toString(),
                        ),
                )
            result.statusCode shouldBe 0
            result.stdout shouldContain customOutput.toString()

            Files.exists(customOutput) shouldBe true
            Files.readString(customOutput) shouldBe "ABXYEFGH"
        }

        "patch should handle identical basis and target (no changes)" {
            val tmp = tempdir().toPath()

            val basisFile = tmp.resolve("same.txt")
            val targetFile = tmp.resolve("same_copy.txt")
            val content = "This content is identical"
            Files.writeString(basisFile, content)
            Files.writeString(targetFile, content)

            val sigFile = tmp.resolve("same.txt.sig")
            SigCmd().test(argv = arrayOf(basisFile.toString(), "-o", sigFile.toString()))

            val deltaFile = tmp.resolve("same_copy.txt.delta")
            DeltaCmd().test(argv = arrayOf(sigFile.toString(), targetFile.toString(), "-o", deltaFile.toString()))

            val reconstructed = tmp.resolve("reconstructed.bin")
            val result =
                PatchCmd().test(
                    argv =
                        arrayOf(
                            basisFile.toString(),
                            deltaFile.toString(),
                            "-o",
                            reconstructed.toString(),
                        ),
                )
            result.statusCode shouldBe 0

            Files.readString(reconstructed) shouldBe content
        }

        "patch should handle binary data correctly" {
            val tmp = tempdir().toPath()

            val basisFile = tmp.resolve("binary_basis.bin")
            val targetFile = tmp.resolve("binary_target.bin")
            val basisBytes = ByteArray(100) { it.toByte() }
            val targetBytes = ByteArray(100) { (it + 50).toByte() }

            Files.write(basisFile, basisBytes)
            Files.write(targetFile, targetBytes)

            val sigFile = tmp.resolve("binary_basis.bin.sig")
            SigCmd().test(argv = arrayOf(basisFile.toString(), "-o", sigFile.toString()))

            val deltaFile = tmp.resolve("binary_target.bin.delta")
            DeltaCmd().test(argv = arrayOf(sigFile.toString(), targetFile.toString(), "-o", deltaFile.toString()))

            val reconstructed = tmp.resolve("reconstructed.bin")
            val result =
                PatchCmd().test(
                    argv =
                        arrayOf(
                            basisFile.toString(),
                            deltaFile.toString(),
                            "-o",
                            reconstructed.toString(),
                        ),
                )
            result.statusCode shouldBe 0

            Files.readAllBytes(reconstructed) shouldBe targetBytes
        }

        "patch should handle empty target reconstruction" {
            val tmp = tempdir().toPath()

            val basisFile = tmp.resolve("nonempty.txt")
            val targetFile = tmp.resolve("empty.txt")
            Files.writeString(basisFile, "Some content")
            Files.writeString(targetFile, "")

            val sigFile = tmp.resolve("nonempty.txt.sig")
            SigCmd().test(argv = arrayOf(basisFile.toString(), "-o", sigFile.toString()))

            val deltaFile = tmp.resolve("empty.txt.delta")
            DeltaCmd().test(argv = arrayOf(sigFile.toString(), targetFile.toString(), "-o", deltaFile.toString()))

            val reconstructed = tmp.resolve("reconstructed.bin")
            val result =
                PatchCmd().test(
                    argv =
                        arrayOf(
                            basisFile.toString(),
                            deltaFile.toString(),
                            "-o",
                            reconstructed.toString(),
                        ),
                )
            result.statusCode shouldBe 0

            Files.readString(reconstructed) shouldBe ""
        }

        "patch should satisfy round-trip property for arbitrary text" {
            checkAll(10, Arb.string(0..2000), Arb.string(0..2000)) { basisContent, targetContent ->
                val tmp = tempdir().toPath()
                val basisFile = tmp.resolve("basis.txt")
                val targetFile = tmp.resolve("target.txt")
                Files.writeString(basisFile, basisContent)
                Files.writeString(targetFile, targetContent)

                val sigFile = tmp.resolve("basis.txt.sig")
                SigCmd().test(argv = arrayOf(basisFile.toString(), "-o", sigFile.toString()))

                val deltaFile = tmp.resolve("target.txt.delta")
                DeltaCmd().test(argv = arrayOf(sigFile.toString(), targetFile.toString(), "-o", deltaFile.toString()))

                val reconstructed = tmp.resolve("reconstructed.txt")
                val result =
                    PatchCmd().test(
                        argv =
                            arrayOf(
                                basisFile.toString(),
                                deltaFile.toString(),
                                "-o",
                                reconstructed.toString(),
                            ),
                    )

                result.statusCode shouldBe 0
                Files.readString(reconstructed) shouldBe targetContent
            }
        }

        "patch should satisfy round-trip property for arbitrary binary data" {
            checkAll(10, Arb.list(Arb.byte(), 0..3000), Arb.list(Arb.byte(), 0..3000)) { basisData, targetData ->
                val tmp = tempdir().toPath()
                val basisFile = tmp.resolve("basis.bin")
                val targetFile = tmp.resolve("target.bin")
                Files.write(basisFile, basisData.toByteArray())
                Files.write(targetFile, targetData.toByteArray())

                val sigFile = tmp.resolve("basis.bin.sig")
                SigCmd().test(argv = arrayOf(basisFile.toString(), "-o", sigFile.toString()))

                val deltaFile = tmp.resolve("target.bin.delta")
                DeltaCmd().test(argv = arrayOf(sigFile.toString(), targetFile.toString(), "-o", deltaFile.toString()))

                val reconstructed = tmp.resolve("reconstructed.bin")
                val result =
                    PatchCmd().test(
                        argv =
                            arrayOf(
                                basisFile.toString(),
                                deltaFile.toString(),
                                "-o",
                                reconstructed.toString(),
                            ),
                    )

                result.statusCode shouldBe 0
                Files.readAllBytes(reconstructed) shouldBe targetData.toByteArray()
            }
        }

        "patch should satisfy round-trip property with various block sizes" {
            checkAll(
                10,
                Arb.list(Arb.byte(), 100..2000),
                Arb.list(Arb.byte(), 100..2000),
                Arb.int(64..512),
            ) { basisData, targetData, blockSize ->
                val tmp = tempdir().toPath()
                val basisFile = tmp.resolve("basis.bin")
                val targetFile = tmp.resolve("target.bin")
                Files.write(basisFile, basisData.toByteArray())
                Files.write(targetFile, targetData.toByteArray())

                val sigFile = tmp.resolve("basis.bin.sig")
                SigCmd().test(
                    argv =
                        arrayOf(
                            basisFile.toString(),
                            "-b",
                            blockSize.toString(),
                            "-o",
                            sigFile.toString(),
                        ),
                )

                val deltaFile = tmp.resolve("target.bin.delta")
                DeltaCmd().test(argv = arrayOf(sigFile.toString(), targetFile.toString(), "-o", deltaFile.toString()))

                val reconstructed = tmp.resolve("reconstructed.bin")
                val result =
                    PatchCmd().test(
                        argv =
                            arrayOf(
                                basisFile.toString(),
                                deltaFile.toString(),
                                "-o",
                                reconstructed.toString(),
                            ),
                    )

                result.statusCode shouldBe 0
                Files.readAllBytes(reconstructed) shouldBe targetData.toByteArray()
            }
        }
    })
