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

/** Tests for diff between commits and working directory. */
class DiffCmdTest :
    StringSpec({

        "diff should show no differences for identical commits" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create and commit a file
            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "Test commit"))

            // Get commit ID from HEAD
            val commitId = Files.readString(repo.refsHeads.resolve("master")).trim()

            val cmd = DiffCmd { repo }
            val result = cmd.test(argv = arrayOf(commitId, commitId))

            result.statusCode shouldBe 0
            result.stdout shouldContain "No differences found"
        }

        "diff should show file addition" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create first empty commit
            CommitCmd { repo }.test(arrayOf("-m", "Empty commit", "--allow-empty"))
            val commit1 = Files.readString(repo.refsHeads.resolve("master")).trim()

            // Create a second commit with a file
            val file = tmp.resolve("added.txt")
            Files.writeString(file, "new content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "added.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "Add file"))
            val commit2 = Files.readString(repo.refsHeads.resolve("master")).trim()

            val cmd = DiffCmd { repo }
            val result = cmd.test(argv = arrayOf(commit1, commit2))

            result.statusCode shouldBe 0
            result.stdout shouldContain "diff --timetree a/added.txt b/added.txt"
            result.stdout shouldContain "new file"
            result.stdout shouldContain "+new content"
        }

        "diff should show file deletion" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val file = tmp.resolve("deleted.txt")
            Files.writeString(file, "to be deleted")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "deleted.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "Add file"))
            val commit1 = Files.readString(repo.refsHeads.resolve("master")).trim()

            val indexPath = repo.meta.resolve("index")
            if (Files.exists(indexPath)) {
                Files.delete(indexPath)
            }
            CommitCmd { repo }.test(arrayOf("-m", "Delete file", "--allow-empty"))
            val commit2 = Files.readString(repo.refsHeads.resolve("master")).trim()

            val cmd = DiffCmd { repo }
            val result = cmd.test(argv = arrayOf(commit1, commit2))

            result.statusCode shouldBe 0
            result.stdout shouldContain "diff --timetree a/deleted.txt b/deleted.txt"
            result.stdout shouldContain "deleted file"
            result.stdout shouldContain "-to be deleted"
        }

        "diff should show file modification" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create first commit
            val file = tmp.resolve("modified.txt")
            Files.writeString(file, "old content\nline2\nline3")
            val blob1 = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "modified.txt", blob1)
            CommitCmd { repo }.test(arrayOf("-m", "Initial"))
            val commit1 = Files.readString(repo.refsHeads.resolve("master")).trim()

            // Modify and commit
            Files.writeString(file, "new content\nline2\nline3")
            val blob2 = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "modified.txt", blob2)
            CommitCmd { repo }.test(arrayOf("-m", "Modified"))
            val commit2 = Files.readString(repo.refsHeads.resolve("master")).trim()

            val cmd = DiffCmd { repo }
            val result = cmd.test(argv = arrayOf(commit1, commit2))

            result.statusCode shouldBe 0
            result.stdout shouldContain "diff --timetree a/modified.txt b/modified.txt"
            result.stdout shouldContain "-old content"
            result.stdout shouldContain "+new content"
            result.stdout shouldContain " line2"
        }

        "diff should handle multiple file changes" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create the first commit with two files
            val file1 = tmp.resolve("file1.txt")
            Files.writeString(file1, "file1 v1")
            val blob1a = FsObjectStore.writeBlob(repo, file1)
            Index.update(repo, "file1.txt", blob1a)

            val file2 = tmp.resolve("file2.txt")
            Files.writeString(file2, "file2 v1")
            val blob2a = FsObjectStore.writeBlob(repo, file2)
            Index.update(repo, "file2.txt", blob2a)

            CommitCmd { repo }.test(arrayOf("-m", "Initial"))
            val commit1 = Files.readString(repo.refsHeads.resolve("master")).trim()

            // Modify both files
            Files.writeString(file1, "file1 v2")
            val blob1b = FsObjectStore.writeBlob(repo, file1)
            Index.update(repo, "file1.txt", blob1b)

            Files.writeString(file2, "file2 v2")
            val blob2b = FsObjectStore.writeBlob(repo, file2)
            Index.update(repo, "file2.txt", blob2b)

            CommitCmd { repo }.test(arrayOf("-m", "Modified both"))
            val commit2 = Files.readString(repo.refsHeads.resolve("master")).trim()

            val cmd = DiffCmd { repo }
            val result = cmd.test(argv = arrayOf(commit1, commit2))

            result.statusCode shouldBe 0
            result.stdout shouldContain "diff --timetree a/file1.txt b/file1.txt"
            result.stdout shouldContain "-file1 v1"
            result.stdout shouldContain "+file1 v2"
            result.stdout shouldContain "diff --timetree a/file2.txt b/file2.txt"
            result.stdout shouldContain "-file2 v1"
            result.stdout shouldContain "+file2 v2"
        }

        "diff should work with abbreviated commit hashes" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create a simple commit
            val file = tmp.resolve("file.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "file.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "Test"))

            val fullHash = Files.readString(repo.refsHeads.resolve("master")).trim()
            val shortHash = fullHash.take(8)

            val cmd = DiffCmd { repo }
            val result = cmd.test(argv = arrayOf(shortHash, shortHash))

            result.statusCode shouldBe 0
            result.stdout shouldContain "No differences found"
        }

        "diff should show error for non-existent commit" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val cmd = DiffCmd { repo }
            val result = cmd.test(argv = arrayOf("abc123", "def456"))

            result.statusCode shouldBe 1
            result.stderr shouldContain "Error:"
            result.stderr shouldContain "No commit found"
        }

        "diff should work with branch names" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create a commit on master
            val file = tmp.resolve("file.txt")
            Files.writeString(file, "master content")
            val blob1 = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "file.txt", blob1)
            CommitCmd { repo }.test(arrayOf("-m", "Master commit"))

            // Create feature branch and modify file
            CheckoutCmd { repo }.test(arrayOf("-b", "feature"))
            Files.writeString(file, "feature content")
            val blob2 = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "file.txt", blob2)
            CommitCmd { repo }.test(arrayOf("-m", "Feature commit"))

            val cmd = DiffCmd { repo }
            val result = cmd.test(argv = arrayOf("master", "feature"))

            result.statusCode shouldBe 0
            result.stdout shouldContain "-master content"
            result.stdout shouldContain "+feature content"
        }

        "diff should respect context lines option" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create a commit with a multi-line file
            val file = tmp.resolve("file.txt")
            Files.writeString(file, "line1\nline2\nline3\nline4\nline5")
            val blob1 = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "file.txt", blob1)
            CommitCmd { repo }.test(arrayOf("-m", "Initial"))
            val commit1 = Files.readString(repo.refsHeads.resolve("master")).trim()

            // Modify the middle line
            Files.writeString(file, "line1\nline2\nCHANGED\nline4\nline5")
            val blob2 = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "file.txt", blob2)
            CommitCmd { repo }.test(arrayOf("-m", "Changed"))
            val commit2 = Files.readString(repo.refsHeads.resolve("master")).trim()

            val cmd = DiffCmd { repo }
            val result = cmd.test(argv = arrayOf("-U", "1", commit1, commit2))

            result.statusCode shouldBe 0
            result.stdout shouldContain "-line3"
            result.stdout shouldContain "+CHANGED"
        }

        "diff should show error for invalid context lines" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val cmd = DiffCmd { repo }
            val result = cmd.test(argv = arrayOf("-U", "-5", "abc", "def"))

            result.statusCode shouldBe 1
            result.stderr shouldContain "context lines must be non-negative"
        }

        "diff should handle nested directory structures" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create nested structure
            val srcDir = tmp.resolve("src/main")
            Files.createDirectories(srcDir)
            val appFile = srcDir.resolve("App.kt")
            Files.writeString(appFile, "src content")
            val blob1 = FsObjectStore.writeBlob(repo, appFile)
            Index.update(repo, "src/main/App.kt", blob1)

            val testDir = tmp.resolve("src/test")
            Files.createDirectories(testDir)
            val testFile = testDir.resolve("AppTest.kt")
            Files.writeString(testFile, "test content")
            val blob2 = FsObjectStore.writeBlob(repo, testFile)
            Index.update(repo, "src/test/AppTest.kt", blob2)

            CommitCmd { repo }.test(arrayOf("-m", "Initial"))
            val commit1 = Files.readString(repo.refsHeads.resolve("master")).trim()

            // Modify only the app file
            Files.writeString(appFile, "modified src content")
            val blob1b = FsObjectStore.writeBlob(repo, appFile)
            Index.update(repo, "src/main/App.kt", blob1b)
            CommitCmd { repo }.test(arrayOf("-m", "Modified"))
            val commit2 = Files.readString(repo.refsHeads.resolve("master")).trim()

            val cmd = DiffCmd { repo }
            val result = cmd.test(argv = arrayOf(commit1, commit2))

            result.statusCode shouldBe 0
            result.stdout shouldContain "diff --timetree a/src/main/App.kt b/src/main/App.kt"
            result.stdout shouldContain "-src content"
            result.stdout shouldContain "+modified src content"
            result.stdout shouldNotContain "AppTest.kt"
        }

        "diff should handle empty lines in files" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val file = tmp.resolve("file.txt")
            Files.writeString(file, "line1\n\nline3")
            val blob1 = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "file.txt", blob1)
            CommitCmd { repo }.test(arrayOf("-m", "Initial"))
            val commit1 = Files.readString(repo.refsHeads.resolve("master")).trim()

            Files.writeString(file, "line1\nline2\nline3")
            val blob2 = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "file.txt", blob2)
            CommitCmd { repo }.test(arrayOf("-m", "Modified"))
            val commit2 = Files.readString(repo.refsHeads.resolve("master")).trim()

            val cmd = DiffCmd { repo }
            val result = cmd.test(argv = arrayOf(commit1, commit2))

            result.statusCode shouldBe 0
            result.stdout shouldContain "+line2"
        }

        "diff without arguments should show working directory changes" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create and commit the initial file
            val file = tmp.resolve("test.txt")
            Files.writeString(file, "original content")
            val blob = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blob)
            CommitCmd { repo }.test(arrayOf("-m", "Initial"))

            // Modify a file in the working directory
            Files.writeString(file, "modified content")

            val cmd = DiffCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "diff --timetree a/test.txt b/test.txt"
            result.stdout shouldContain "index"
            result.stdout shouldContain ".."
            result.stdout shouldContain "100644"
            result.stdout shouldContain "-original content"
            result.stdout shouldContain "+modified content"
        }

        "diff --cached should show staged changes" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create and commit the initial file
            val file = tmp.resolve("test.txt")
            Files.writeString(file, "original content")
            val blob1 = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blob1)
            CommitCmd { repo }.test(arrayOf("-m", "Initial"))

            // Modify and stage
            Files.writeString(file, "staged content")
            val blob2 = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blob2)

            val cmd = DiffCmd { repo }
            val result = cmd.test(arrayOf("--cached"))

            result.statusCode shouldBe 0
            result.stdout shouldContain "diff --timetree a/test.txt b/test.txt"
            result.stdout shouldContain "-original content"
            result.stdout shouldContain "+staged content"
        }

        "diff should show nothing when working directory matches index" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create and commit a file
            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blob = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blob)
            CommitCmd { repo }.test(arrayOf("-m", "Initial"))

            val cmd = DiffCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "No changes in working directory"
        }

        "diff should detect deleted files in working directory" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create and commit a file
            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content to delete")
            val blob = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blob)
            CommitCmd { repo }.test(arrayOf("-m", "Initial"))

            // Delete a file from the working directory
            Files.delete(file)

            val cmd = DiffCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "diff --timetree a/test.txt b/test.txt"
            result.stdout shouldContain "deleted file"
            result.stdout shouldContain "-content to delete"
        }

        "diff should handle multiple modified files in working directory" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create and commit multiple files
            val file1 = tmp.resolve("file1.txt")
            Files.writeString(file1, "content1")
            val blob1 = FsObjectStore.writeBlob(repo, file1)
            Index.update(repo, "file1.txt", blob1)

            val file2 = tmp.resolve("file2.txt")
            Files.writeString(file2, "content2")
            val blob2 = FsObjectStore.writeBlob(repo, file2)
            Index.update(repo, "file2.txt", blob2)

            CommitCmd { repo }.test(arrayOf("-m", "Initial"))

            // Modify both files
            Files.writeString(file1, "modified1")
            Files.writeString(file2, "modified2")

            val cmd = DiffCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "diff --timetree a/file1.txt b/file1.txt"
            result.stdout shouldContain "-content1"
            result.stdout shouldContain "+modified1"
            result.stdout shouldContain "diff --timetree a/file2.txt b/file2.txt"
            result.stdout shouldContain "-content2"
            result.stdout shouldContain "+modified2"
        }
    })
