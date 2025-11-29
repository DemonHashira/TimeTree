package dev.kamisama.cli.commands

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
 * Tests for file staging.
 */
class AddCmdTest :
    StringSpec({

        "add should normalize stored path to repo-root-relative" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val nested = tmp.resolve("dir/sub/hello.txt")
            Files.createDirectories(nested.parent)
            Files.writeString(nested, "hi")

            val cmd = AddCmd { repo }
            val result = cmd.test(argv = arrayOf(nested.toAbsolutePath().toString()))
            result.statusCode shouldBe 0
            result.stdout shouldContain "add 'dir/sub/hello.txt'"

            val index = Files.readAllLines(repo.meta.resolve("index"))
            index.any { it.endsWith(" dir/sub/hello.txt") } shouldBe true
        }

        "add should skip files inside .timetree (internal metadata)" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val head = repo.head
            val cmd = AddCmd { repo }
            val result = cmd.test(argv = arrayOf(head.toString()))
            result.statusCode shouldBe 0
            result.stdout shouldContain "Nothing specified, nothing added."

            val indexPath = repo.meta.resolve("index")
            val lines = if (Files.exists(indexPath)) Files.readAllLines(indexPath) else emptyList<String>()
            lines.shouldBeEmpty()
        }
        "add should expand glob patterns and stage matching files" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            Files.writeString(tmp.resolve("test1.txt"), "content1")
            Files.writeString(tmp.resolve("test2.txt"), "content2")
            Files.writeString(tmp.resolve("readme.md"), "readme")

            val cmd = AddCmd { repo }
            val result = cmd.test(argv = arrayOf("*.txt"))
            result.statusCode shouldBe 0
            result.stdout shouldContain "add 'test1.txt'"
            result.stdout shouldContain "add 'test2.txt'"

            val index = Files.readAllLines(repo.meta.resolve("index"))
            index.any { it.endsWith(" test1.txt") } shouldBe true
            index.any { it.endsWith(" test2.txt") } shouldBe true
            index.any { it.endsWith(" readme.md") } shouldBe false
        }

        "add should recursively stage all files in a directory" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val dir = tmp.resolve("src/main")
            Files.createDirectories(dir)
            Files.writeString(dir.resolve("App.kt"), "app code")
            Files.writeString(dir.resolve("Utils.kt"), "utils code")

            val cmd = AddCmd { repo }
            val result = cmd.test(argv = arrayOf("src"))
            result.statusCode shouldBe 0
            result.stdout shouldContain "add 'src/main/App.kt'"
            result.stdout shouldContain "add 'src/main/Utils.kt'"

            val index = Files.readAllLines(repo.meta.resolve("index"))
            index.any { it.endsWith(" src/main/App.kt") } shouldBe true
            index.any { it.endsWith(" src/main/Utils.kt") } shouldBe true
        }

        "add . should stage all files in repository root recursively" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            Files.writeString(tmp.resolve("root.txt"), "root")
            val nested = tmp.resolve("dir/nested.txt")
            Files.createDirectories(nested.parent)
            Files.writeString(nested, "nested")

            val cmd = AddCmd { repo }
            val result = cmd.test(argv = arrayOf("."))
            result.statusCode shouldBe 0
            result.stdout shouldContain "add 'root.txt'"
            result.stdout shouldContain "add 'dir/nested.txt'"

            val index = Files.readAllLines(repo.meta.resolve("index"))
            index.any { it.endsWith(" root.txt") } shouldBe true
            index.any { it.endsWith(" dir/nested.txt") } shouldBe true
        }

        "add . should skip .timetree directory when staging all files" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            Files.writeString(tmp.resolve("tracked.txt"), "track me")

            val cmd = AddCmd { repo }
            val result = cmd.test(argv = arrayOf("."))
            result.statusCode shouldBe 0
            result.stdout shouldContain "add 'tracked.txt'"

            val index = Files.readAllLines(repo.meta.resolve("index"))
            index.any { it.endsWith(" tracked.txt") } shouldBe true
            index.none { it.contains(".timetree") } shouldBe true
        }

        "add should handle non-existent files gracefully" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val cmd = AddCmd { repo }
            val result = cmd.test(argv = arrayOf("missing.txt"))
            result.statusCode shouldBe 0
            result.stdout shouldContain "Nothing specified, nothing added."

            val indexPath = repo.meta.resolve("index")
            val lines = if (Files.exists(indexPath)) Files.readAllLines(indexPath) else emptyList()
            lines.shouldBeEmpty()
        }

        "add should skip files that are already staged with same content" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            Files.writeString(tmp.resolve("unchanged.txt"), "same content")
            val cmd = AddCmd { repo }
            val result1 = cmd.test(argv = arrayOf("unchanged.txt"))
            result1.statusCode shouldBe 0
            result1.stdout shouldContain "add 'unchanged.txt'"

            val result2 = cmd.test(argv = arrayOf("unchanged.txt"))
            result2.statusCode shouldBe 0
            result2.stdout shouldNotContain "add 'unchanged.txt'"
            result2.stdout shouldContain "All 1 file(s) already staged and up-to-date"
        }

        "add should re-stage files when content changes" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val file = tmp.resolve("modified.txt")
            Files.writeString(file, "original content")
            val cmd = AddCmd { repo }
            val result1 = cmd.test(argv = arrayOf("modified.txt"))
            result1.statusCode shouldBe 0
            result1.stdout shouldContain "add 'modified.txt'"

            Files.writeString(file, "changed content")

            val result2 = cmd.test(argv = arrayOf("modified.txt"))
            result2.statusCode shouldBe 0
            result2.stdout shouldContain "update 'modified.txt'"
        }
    })
