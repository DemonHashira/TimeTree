package dev.kamisama.cli.commands

import com.github.ajalt.clikt.testing.test
import dev.kamisama.core.fs.RepoLayout
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

/**
 * Tests for repository initialization.
 */
class InitCmdTest :
    StringSpec({

        "InitCmd should create .timetree with objects, refs, and HEAD" {
            val tempDir = tempdir()
            val repoLayout = RepoLayout(tempDir.toPath())

            val result = InitCmd(repoProvider = { repoLayout }).test(argv = emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "Initialized TimeTree repo"

            Files.isDirectory(repoLayout.meta) shouldBe true
            Files.isDirectory(repoLayout.objects) shouldBe true
            Files.isDirectory(repoLayout.refsHeads) shouldBe true
            Files.exists(repoLayout.head) shouldBe true
            Files.readString(repoLayout.head) shouldBe "ref: refs/heads/master\n"
        }

        "InitCmd should print a different message if repo already exists" {
            val tempDir = tempdir()
            val repoLayout = RepoLayout(tempDir.toPath())
            val cmd = InitCmd { repoLayout }

            val first = cmd.test(argv = emptyArray())
            first.statusCode shouldBe 0
            first.stdout shouldContain "Initialized TimeTree repo"

            val second = cmd.test(argv = emptyArray())
            second.statusCode shouldBe 0
            second.stdout shouldContain "Reinitialized existing TimeTree repository"
        }

        "InitCmd should fail when .timetree exists as a file" {
            val tempDir = tempdir()
            val repoLayout = RepoLayout(tempDir.toPath())

            Files.writeString(repoLayout.meta, "I'm a file, not a directory!")

            val result = InitCmd(repoProvider = { repoLayout }).test(argv = emptyArray())

            result.statusCode shouldBe 1
            result.stderr shouldContain "exists but is not a directory"
        }

        "InitCmd should fail when root directory is not writable" {
            val tempDir = tempdir()
            val repoLayout = RepoLayout(tempDir.toPath())

            tempDir.setWritable(false)

            try {
                val result = InitCmd(repoProvider = { repoLayout }).test(argv = emptyArray())

                result.statusCode shouldBe 1
                result.stderr shouldContain "No write permission"
            } finally {
                tempDir.setWritable(true)
            }
        }

        "InitCmd should fail with descriptive error on IOException during initialization" {
            val tempDir = tempdir()
            val repoLayout = RepoLayout(tempDir.toPath())

            Files.createDirectories(repoLayout.meta)
            Files.writeString(repoLayout.objects, "blocking file")

            val result = InitCmd(repoProvider = { repoLayout }).test(argv = emptyArray())

            result.statusCode shouldBe 1
            result.stderr shouldContain "Failed to initialize repository"
        }

        "InitCmd should repair partially initialized repository" {
            val tempDir = tempdir()
            val repoLayout = RepoLayout(tempDir.toPath())

            Files.createDirectories(repoLayout.objects)

            val result = InitCmd(repoProvider = { repoLayout }).test(argv = emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "Initialized TimeTree repo"

            Files.isDirectory(repoLayout.refsHeads) shouldBe true
            Files.exists(repoLayout.head) shouldBe true
        }
    })
