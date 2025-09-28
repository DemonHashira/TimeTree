package dev.kamisama.cli

import com.github.ajalt.clikt.testing.test
import dev.kamisama.core.fs.RepoLayout
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

/**
 * Tests for the InitCmd functionality - verifies repository initialization.
 */
class InitCmdTest :
    StringSpec({

        "InitCmd should create .timetree with objects, refs, and HEAD" {
            val tempDir = tempdir()
            val repoLayout = RepoLayout(tempDir.toPath())

            val result = InitCmd(repoProvider = { repoLayout }).test(argv = emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "Initialized TimeTree repo"

            // Verify the repository structure was created
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

            // First init
            val first = cmd.test(argv = emptyArray())
            first.statusCode shouldBe 0
            first.stdout shouldContain "Initialized TimeTree repo"

            // Second init - should detect an existing repository
            val second = cmd.test(argv = emptyArray())
            second.statusCode shouldBe 0
            second.stdout shouldContain "Reinitialized existing TimeTree repository"
        }
    })
