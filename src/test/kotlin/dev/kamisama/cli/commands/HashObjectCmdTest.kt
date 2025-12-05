package dev.kamisama.cli.commands

import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

/**
 * Tests for hash-object command.
 */
class HashObjectCmdTest :
    StringSpec({

        "hash-object should compute both git-style and timetree hashes" {
            val tmp = tempdir().toPath()
            val testFile = tmp.resolve("test.txt")
            Files.writeString(testFile, "hello world")

            val cmd = HashObjectCmd()
            val result = cmd.test(argv = arrayOf(testFile.toString()))
            result.statusCode shouldBe 0
            result.stdout shouldContain "git-style:"
            result.stdout shouldContain "timetree:"

            val lines = result.stdout.trim().lines()
            lines.size shouldBe 2
            lines[0] shouldContain "git-style:"
            lines[1] shouldContain "timetree:"
        }

        "hash-object should produce valid 40-character hex hashes" {
            val tmp = tempdir().toPath()
            val testFile = tmp.resolve("data.bin")
            Files.write(testFile, ByteArray(100) { it.toByte() })

            val cmd = HashObjectCmd()
            val result = cmd.test(argv = arrayOf(testFile.toString()))
            result.statusCode shouldBe 0

            val lines = result.stdout.trim().lines()
            val gitHash = lines[0].substringAfter("git-style: ").trim()
            val ttHash = lines[1].substringAfter("timetree: ").trim()

            gitHash.length shouldBe 40
            ttHash.length shouldBe 40
            gitHash.all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
            ttHash.all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
        }

        "hash-object should produce different hashes for git-style vs timetree" {
            val tmp = tempdir().toPath()
            val testFile = tmp.resolve("content.txt")
            Files.writeString(testFile, "test content")

            val cmd = HashObjectCmd()
            val result = cmd.test(argv = arrayOf(testFile.toString()))
            result.statusCode shouldBe 0

            val lines = result.stdout.trim().lines()
            val gitHash = lines[0].substringAfter("git-style: ").trim()
            val ttHash = lines[1].substringAfter("timetree: ").trim()

            gitHash.length shouldBe 40
            ttHash.length shouldBe 40
            gitHash shouldNotBe ttHash
        }

        "hash-object should fail for non-existent files" {
            val tmp = tempdir().toPath()
            val nonExistent = tmp.resolve("does-not-exist.txt")

            val cmd = HashObjectCmd()
            val result =
                runCatching {
                    cmd.test(argv = arrayOf(nonExistent.toString()))
                }
            result.isFailure shouldBe true
        }

        "hash-object should fail for directories" {
            val tmp = tempdir().toPath()
            val directory = tmp.resolve("some-dir")
            Files.createDirectory(directory)

            val cmd = HashObjectCmd()
            val result =
                runCatching {
                    cmd.test(argv = arrayOf(directory.toString()))
                }
            result.isFailure shouldBe true
        }

        "hash-object should handle empty files" {
            val tmp = tempdir().toPath()
            val emptyFile = tmp.resolve("empty.txt")
            Files.writeString(emptyFile, "")

            val cmd = HashObjectCmd()
            val result = cmd.test(argv = arrayOf(emptyFile.toString()))
            result.statusCode shouldBe 0
            result.stdout shouldContain "git-style:"
            result.stdout shouldContain "timetree:"

            val lines = result.stdout.trim().lines()
            val gitHash = lines[0].substringAfter("git-style: ").trim()
            val ttHash = lines[1].substringAfter("timetree: ").trim()

            gitHash shouldBe "e69de29bb2d1d6434b8b29ae775ad8c2e48c5391"
            ttHash.length shouldBe 40
            gitHash shouldNotBe ttHash
        }

        "hash-object should produce consistent hashes for same content" {
            val tmp = tempdir().toPath()
            val file1 = tmp.resolve("file1.txt")
            val file2 = tmp.resolve("file2.txt")
            val content = "identical content"
            Files.writeString(file1, content)
            Files.writeString(file2, content)

            val cmd1 = HashObjectCmd()
            val result1 = cmd1.test(argv = arrayOf(file1.toString()))

            val cmd2 = HashObjectCmd()
            val result2 = cmd2.test(argv = arrayOf(file2.toString()))

            result1.stdout shouldBe result2.stdout
        }

        "hash-object should handle large files" {
            val tmp = tempdir().toPath()
            val largeFile = tmp.resolve("large.bin")
            Files.write(largeFile, ByteArray(1024 * 1024) { (it % 256).toByte() })

            val cmd = HashObjectCmd()
            val result = cmd.test(argv = arrayOf(largeFile.toString()))
            result.statusCode shouldBe 0
            result.stdout shouldContain "git-style:"
            result.stdout shouldContain "timetree:"

            val lines = result.stdout.trim().lines()
            val gitHash = lines[0].substringAfter("git-style: ").trim()
            val ttHash = lines[1].substringAfter("timetree: ").trim()

            gitHash.length shouldBe 40
            ttHash.length shouldBe 40
        }
    })
