package dev.kamisama.cli

import com.github.ajalt.clikt.testing.test
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.fs.ensureInitialized
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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
        "add should expand glob patterns and stage matching files" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create multiple files with similar names
            Files.writeString(tmp.resolve("test1.txt"), "content1")
            Files.writeString(tmp.resolve("test2.txt"), "content2")
            Files.writeString(tmp.resolve("readme.md"), "readme")

            val cmd = AddCmd { repo }
            val result = cmd.test(argv = arrayOf("*.txt"))
            result.statusCode shouldBe 0
            result.stdout shouldContain "staged: test1.txt ->"
            result.stdout shouldContain "staged: test2.txt ->"

            val index = Files.readAllLines(repo.meta.resolve("index"))
            index.any { it.endsWith(" test1.txt") } shouldBe true
            index.any { it.endsWith(" test2.txt") } shouldBe true
            index.any { it.endsWith(" readme.md") } shouldBe false
        }

        "add should recursively stage all files in a directory" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create nested directory structure
            val dir = tmp.resolve("src/main")
            Files.createDirectories(dir)
            Files.writeString(dir.resolve("App.kt"), "app code")
            Files.writeString(dir.resolve("Utils.kt"), "utils code")

            val cmd = AddCmd { repo }
            val result = cmd.test(argv = arrayOf("src"))
            result.statusCode shouldBe 0
            result.stdout shouldContain "staged: src/main/App.kt ->"
            result.stdout shouldContain "staged: src/main/Utils.kt ->"

            val index = Files.readAllLines(repo.meta.resolve("index"))
            index.any { it.endsWith(" src/main/App.kt") } shouldBe true
            index.any { it.endsWith(" src/main/Utils.kt") } shouldBe true
        }

        "add . should stage all files in repository root recursively" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create files at different levels
            Files.writeString(tmp.resolve("root.txt"), "root")
            val nested = tmp.resolve("dir/nested.txt")
            Files.createDirectories(nested.parent)
            Files.writeString(nested, "nested")

            val cmd = AddCmd { repo }
            val result = cmd.test(argv = arrayOf("."))
            result.statusCode shouldBe 0
            result.stdout shouldContain "staged: root.txt ->"
            result.stdout shouldContain "staged: dir/nested.txt ->"

            val index = Files.readAllLines(repo.meta.resolve("index"))
            index.any { it.endsWith(" root.txt") } shouldBe true
            index.any { it.endsWith(" dir/nested.txt") } shouldBe true
        }

        "add . should skip .timetree directory when staging all files" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create a regular file and internal metadata file
            Files.writeString(tmp.resolve("tracked.txt"), "track me")

            val cmd = AddCmd { repo }
            val result = cmd.test(argv = arrayOf("."))
            result.statusCode shouldBe 0
            result.stdout shouldContain "staged: tracked.txt ->"

            val index = Files.readAllLines(repo.meta.resolve("index"))
            index.any { it.endsWith(" tracked.txt") } shouldBe true
            // Ensure no .timetree paths leaked into the index
            index.none { it.contains(".timetree") } shouldBe true
        }

        "add should handle non-existent files gracefully" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val cmd = AddCmd { repo }
            val result = cmd.test(argv = arrayOf("missing.txt"))
            result.statusCode shouldBe 0
            result.stdout shouldContain "skip: missing.txt (not found or not a regular file)"

            val indexPath = repo.meta.resolve("index")
            val lines = if (Files.exists(indexPath)) Files.readAllLines(indexPath) else emptyList()
            lines.shouldBeEmpty()
        }

        "add should skip files that are already staged with same content" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create a file and stage it
            Files.writeString(tmp.resolve("unchanged.txt"), "same content")
            val cmd = AddCmd { repo }
            val result1 = cmd.test(argv = arrayOf("unchanged.txt"))
            result1.statusCode shouldBe 0
            result1.stdout shouldContain "staged: unchanged.txt ->"

            // Try to stage the same file again without changes
            val result2 = cmd.test(argv = arrayOf("unchanged.txt"))
            result2.statusCode shouldBe 0
            // Should not show a "staged" message since a file hasn't changed
            result2.stdout shouldNotContain "staged: unchanged.txt ->"
            // Should show a "nothing to stage" message
            result2.stdout shouldContain "Nothing to stage - all files are already up to date."
        }

        "add should re-stage files when content changes" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create and stage a file
            val file = tmp.resolve("modified.txt")
            Files.writeString(file, "original content")
            val cmd = AddCmd { repo }
            val result1 = cmd.test(argv = arrayOf("modified.txt"))
            result1.statusCode shouldBe 0
            result1.stdout shouldContain "staged: modified.txt ->"

            // Modify the file
            Files.writeString(file, "changed content")

            // Stage it again - should show a staged message since content changed
            val result2 = cmd.test(argv = arrayOf("modified.txt"))
            result2.statusCode shouldBe 0
            result2.stdout shouldContain "staged: modified.txt ->"
        }
    })
