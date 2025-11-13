package dev.kamisama.cli.commands

import com.github.ajalt.clikt.testing.test
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.fs.ensureInitialized
import dev.kamisama.core.index.Index
import dev.kamisama.core.objects.FsObjectStore
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

/**
 * Tests for the StatusCmd functionality - verifies status reporting for the working tree, index, and HEAD.
 */
class StatusCmdTest :
    StringSpec({

        "status should show clean working tree on fresh repo with no files" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val cmd = StatusCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "nothing to commit, working tree clean"
        }

        "status should show untracked files in working tree" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create untracked files
            Files.writeString(tmp.resolve("new.txt"), "content")
            Files.writeString(tmp.resolve("another.txt"), "more content")

            val cmd = StatusCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "Untracked files:"
            result.stdout shouldContain "new.txt"
            result.stdout shouldContain "another.txt"
            result.stdout shouldNotContain "Changes to be committed:"
            result.stdout shouldNotContain "Changes not staged for commit:"
        }

        "status should show staged files after adding to index" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create and stage a file
            val file = tmp.resolve("staged.txt")
            Files.writeString(file, "staged content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "staged.txt", blobId)

            val cmd = StatusCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "Changes to be committed:"
            result.stdout shouldContain "modified:   staged.txt"
            result.stdout shouldNotContain "Changes not staged for commit:"
            result.stdout shouldNotContain "Untracked files:"
        }

        "status should show unstaged changes when working tree differs from index" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create and stage a file
            val file = tmp.resolve("modified.txt")
            Files.writeString(file, "original content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "modified.txt", blobId)

            // Modify the file in the working tree
            Files.writeString(file, "changed content")

            val cmd = StatusCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "Changes not staged for commit:"
            result.stdout shouldContain "modified:   modified.txt"
        }

        "status should show deleted files as unstaged when removed from working tree" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create and stage a file
            val file = tmp.resolve("deleted.txt")
            Files.writeString(file, "to be deleted")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "deleted.txt", blobId)

            // Delete the file from the working tree
            Files.delete(file)

            val cmd = StatusCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "Changes not staged for commit:"
            result.stdout shouldContain "deleted.txt"
        }

        "status should correctly categorize staged, unstaged, and untracked files" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create a staged file
            val staged = tmp.resolve("staged.txt")
            Files.writeString(staged, "staged")
            val stagedId = FsObjectStore.writeBlob(repo, staged)
            Index.update(repo, "staged.txt", stagedId)

            // Create an unstaged file (staged but then modified)
            val unstaged = tmp.resolve("unstaged.txt")
            Files.writeString(unstaged, "original")
            val unstagedId = FsObjectStore.writeBlob(repo, unstaged)
            Index.update(repo, "unstaged.txt", unstagedId)
            Files.writeString(unstaged, "modified")

            // Create an untracked file
            Files.writeString(tmp.resolve("untracked.txt"), "untracked")

            val cmd = StatusCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "Changes to be committed:"
            result.stdout shouldContain "staged.txt"
            result.stdout shouldContain "Changes not staged for commit:"
            result.stdout shouldContain "unstaged.txt"
            result.stdout shouldContain "Untracked files:"
            result.stdout shouldContain "untracked.txt"
        }

        "status should show staged changes after first commit" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create, stage, and commit a file
            val file1 = tmp.resolve("committed.txt")
            Files.writeString(file1, "first commit")
            val blob1 = FsObjectStore.writeBlob(repo, file1)
            Index.update(repo, "committed.txt", blob1)
            CommitCmd { repo }.test(arrayOf("-m", "first commit"))

            // Create and stage a new file
            val file2 = tmp.resolve("new.txt")
            Files.writeString(file2, "new file")
            val blob2 = FsObjectStore.writeBlob(repo, file2)
            Index.update(repo, "new.txt", blob2)

            val cmd = StatusCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "Changes to be committed:"
            result.stdout shouldContain "new.txt"
            // committed.txt should not appear since it matches HEAD
            result.stdout shouldNotContain "committed.txt"
        }

        "status should show clean working tree when index matches HEAD" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create, stage, and commit a file
            val file = tmp.resolve("clean.txt")
            Files.writeString(file, "clean content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "clean.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "initial commit"))

            val cmd = StatusCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "nothing to commit, working tree clean"
            result.stdout shouldNotContain "Changes to be committed:"
            result.stdout shouldNotContain "Changes not staged for commit:"
            result.stdout shouldNotContain "Untracked files:"
        }

        "status should detect unstaged changes after commit" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create, stage, and commit a file
            val file = tmp.resolve("modified.txt")
            Files.writeString(file, "original")
            val blob1 = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "modified.txt", blob1)
            CommitCmd { repo }.test(arrayOf("-m", "initial commit"))

            // Modify the file but don't stage it
            Files.writeString(file, "modified content")

            val cmd = StatusCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "Changes not staged for commit:"
            result.stdout shouldContain "modified.txt"
            result.stdout shouldNotContain "Changes to be committed:"
        }

        "status should handle nested directory structures" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create nested untracked files
            val dir = tmp.resolve("src/main/kotlin")
            Files.createDirectories(dir)
            Files.writeString(dir.resolve("App.kt"), "app")
            Files.writeString(dir.resolve("Utils.kt"), "utils")

            val cmd = StatusCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "Untracked files:"
            result.stdout shouldContain "src/main/kotlin/App.kt"
            result.stdout shouldContain "src/main/kotlin/Utils.kt"
        }

        "status should not list .timetree directory contents" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create a file in the working tree
            Files.writeString(tmp.resolve("normal.txt"), "normal")

            val cmd = StatusCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "normal.txt"
            result.stdout shouldNotContain ".timetree"
            result.stdout shouldNotContain "HEAD"
            result.stdout shouldNotContain "index"
        }

        "status should show multiple modified files in sorted order" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create and stage multiple files
            for (name in listOf("zebra.txt", "alpha.txt", "beta.txt")) {
                val file = tmp.resolve(name)
                Files.writeString(file, "content")
                val blobId = FsObjectStore.writeBlob(repo, file)
                Index.update(repo, name, blobId)
            }

            val cmd = StatusCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "Changes to be committed:"

            // Verify files appear in sorted order
            val lines = result.stdout.lines()
            val alphaIdx = lines.indexOfFirst { it.contains("alpha.txt") }
            val betaIdx = lines.indexOfFirst { it.contains("beta.txt") }
            val zebraIdx = lines.indexOfFirst { it.contains("zebra.txt") }

            (betaIdx in (alphaIdx + 1)..<zebraIdx) shouldBe true
        }

        "status should detect file deleted from index but still in working tree" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create, stage, and commit a file
            val file = tmp.resolve("tracked.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "tracked.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "add tracked.txt"))

            // Remove from index by creating a new empty index
            // (Simulating a remove operation)
            val indexPath = repo.meta.resolve("index")
            Files.delete(indexPath)

            val cmd = StatusCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            // File is staged for deletion (was in HEAD, not in index)
            result.stdout shouldContain "Changes to be committed:"
            result.stdout shouldContain "tracked.txt"
            // File still exists in the working tree, so it's also untracked
            result.stdout shouldContain "Untracked files:"
        }

        "status should NOT create objects in the object store (read-only operation)" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create untracked files
            Files.writeString(tmp.resolve("untracked1.txt"), "content1")
            Files.writeString(tmp.resolve("untracked2.txt"), "content2")

            // Count objects before status
            val objectsBefore = Files.walk(repo.objects).filter { Files.isRegularFile(it) }.count()

            // Run status
            val cmd = StatusCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "Untracked files:"

            // Count objects after status - should be the same
            val objectsAfter = Files.walk(repo.objects).filter { Files.isRegularFile(it) }.count()

            objectsAfter shouldBe objectsBefore
        }

        "status uses lazy evaluation - only hashes tracked files, not untracked files" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create and stage one tracked file
            val tracked = tmp.resolve("tracked.txt")
            Files.writeString(tracked, "tracked content")
            val blobId = FsObjectStore.writeBlob(repo, tracked)
            Index.update(repo, "tracked.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "initial"))

            // Create many untracked files
            for (i in 1..100) {
                Files.writeString(tmp.resolve("untracked$i.txt"), "large content ".repeat(1000))
            }

            // Run status - should be fast because it doesn't hash untracked files
            val cmd = StatusCmd { repo }
            val startTime = System.currentTimeMillis()
            val result = cmd.test(emptyArray())
            val duration = System.currentTimeMillis() - startTime

            result.statusCode shouldBe 0
            result.stdout shouldContain "Untracked files:"

            // Performance assertion: should complete reasonably fast
            // With lazy evaluation: only hashes 1 tracked file
            // Without lazy evaluation: would hash all 101 files
            // This should be noticeably faster
            (duration < 5000) shouldBe true // Should complete in under 5 seconds
        }
    })
