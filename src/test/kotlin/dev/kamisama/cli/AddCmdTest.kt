package dev.kamisama.cli

import com.github.ajalt.clikt.testing.test
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.fs.ensureInitialized
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

/**
 * Tests for the AddCmd functionality - verifies file staging behavior.
 */
class AddCmdTest :
    StringSpec({

        "add should normalize stored path to repo-root-relative" {
            // Set up a temporary repository for testing
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create a nested file and pass an ABSOLUTE path to the command
            val nested = tmp.resolve("dir/sub/hello.txt")
            Files.createDirectories(nested.parent)
            Files.writeString(nested, "hi")

            // Test that absolute paths get normalized to relative paths in the index
            val cmd = AddCmd { repo }
            val result = cmd.test(argv = arrayOf(nested.toAbsolutePath().toString()))
            result.statusCode shouldBe 0
            result.stdout shouldContain "staged: dir/sub/hello.txt ->"

            // Verify index contains a normalized relative path
            val index = Files.readAllLines(repo.meta.resolve("index"))
            index.any { it.endsWith(" dir/sub/hello.txt") } shouldBe true
        }

        "add should skip files inside .timetree (internal metadata)" {
            // Set up a temporary repository for testing
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Try to stage HEAD file (should be blocked as internal metadata)
            val head = repo.head
            val cmd = AddCmd { repo }
            val result = cmd.test(argv = arrayOf(head.toString()))
            result.statusCode shouldBe 0
            result.stdout shouldContain "skip: $head (internal .timetree directory)"

            // Verify the index is empty (no internal .timetree files were added)
            val indexPath = repo.meta.resolve("index")
            val lines = if (Files.exists(indexPath)) Files.readAllLines(indexPath) else emptyList<String>()
            lines.shouldBeEmpty()
        }
    })
