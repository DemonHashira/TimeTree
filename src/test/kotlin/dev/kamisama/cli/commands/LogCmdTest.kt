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

/** Tests for commit history display. */
class LogCmdTest :
    StringSpec({

        "log should show 'No commits yet' on fresh repository" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val cmd = LogCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "No commits yet"
        }

        "log should display single commit with abbreviated ID, author, date, and message" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create and commit a file
            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)

            CommitCmd { repo }.test(
                arrayOf(
                    "-m",
                    "Initial commit",
                    "--author-name",
                    "Test User",
                    "--author-email",
                    "test@example.com",
                ),
            )

            val cmd = LogCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "commit"
            result.stdout shouldContain "Author: Test User <test@example.com>"
            result.stdout shouldContain "Date:"
            result.stdout shouldContain "Initial commit"
        }

        "log should display multiple commits in reverse chronological order" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // First commit
            val file1 = tmp.resolve("file1.txt")
            Files.writeString(file1, "content1")
            val blob1 = FsObjectStore.writeBlob(repo, file1)
            Index.update(repo, "file1.txt", blob1)
            CommitCmd { repo }.test(arrayOf("-m", "First commit"))

            // Second commit
            val file2 = tmp.resolve("file2.txt")
            Files.writeString(file2, "content2")
            val blob2 = FsObjectStore.writeBlob(repo, file2)
            Index.update(repo, "file2.txt", blob2)
            CommitCmd { repo }.test(arrayOf("-m", "Second commit"))

            // Third commit
            val file3 = tmp.resolve("file3.txt")
            Files.writeString(file3, "content3")
            val blob3 = FsObjectStore.writeBlob(repo, file3)
            Index.update(repo, "file3.txt", blob3)
            CommitCmd { repo }.test(arrayOf("-m", "Third commit"))

            val cmd = LogCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0

            result.stdout shouldContain "First commit"
            result.stdout shouldContain "Second commit"
            result.stdout shouldContain "Third commit"

            val firstIdx = result.stdout.indexOf("First commit")
            val secondIdx = result.stdout.indexOf("Second commit")
            val thirdIdx = result.stdout.indexOf("Third commit")

            (secondIdx in (thirdIdx + 1)..<firstIdx) shouldBe true
        }

        "log should show abbreviated commit IDs" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "Test commit"))

            val cmd = LogCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0

            val commitLine = result.stdout.lines().first { it.startsWith("commit ") }
            val afterCommit = commitLine.removePrefix("commit ").trim()
            val commitId = afterCommit.split(" ", "(")[0].trim()

            commitId.length shouldBe 40
            commitId.all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
        }

        "log with -n flag should limit number of commits shown" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create 5 commits
            for (i in 1..5) {
                val file = tmp.resolve("file$i.txt")
                Files.writeString(file, "content$i")
                val blob = FsObjectStore.writeBlob(repo, file)
                Index.update(repo, "file$i.txt", blob)
                CommitCmd { repo }.test(arrayOf("-m", "Commit $i"))
            }

            // Request only 2 commits
            val cmd = LogCmd { repo }
            val result = cmd.test(arrayOf("-n", "2"))

            result.statusCode shouldBe 0

            result.stdout shouldContain "Commit 5"
            result.stdout shouldContain "Commit 4"

            result.stdout shouldNotContain "Commit 3"
            result.stdout shouldNotContain "Commit 2"
            result.stdout shouldNotContain "Commit 1"
        }

        "log should handle multiline commit messages by showing only first line" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)

            val multilineMessage =
                """
                First line of commit

                This is the body of the commit message.
                It has multiple lines.
                """.trimIndent()

            CommitCmd { repo }.test(arrayOf("-m", multilineMessage))

            val cmd = LogCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0

            result.stdout shouldContain "First line of commit"

            val lines = result.stdout.lines()
            val messageLineIdx = lines.indexOfFirst { it.contains("First line of commit") }
            val messageLine = lines[messageLineIdx]

            messageLine.trim() shouldBe "First line of commit"
        }

        "log should format timestamp in Git style" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "Test commit"))

            val cmd = LogCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0

            val dateLine = result.stdout.lines().first { it.startsWith("Date:") }

            dateLine shouldContain Regex("""(Mon|Tue|Wed|Thu|Fri|Sat|Sun)""")
            dateLine shouldContain Regex("""(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)""")
            dateLine shouldContain Regex("""\d{2}:\d{2}:\d{2}""")
            dateLine shouldContain Regex("""\d{4}""")
            dateLine shouldContain Regex("""[+-]\d{4}""")
        }

        "log should show author name and email" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)

            CommitCmd { repo }.test(
                arrayOf(
                    "-m",
                    "Test commit",
                    "--author-name",
                    "John Doe",
                    "--author-email",
                    "john@example.com",
                ),
            )

            val cmd = LogCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "Author: John Doe <john@example.com>"
        }

        "log should handle commits with no parent (initial commit)" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "Initial commit"))

            val cmd = LogCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "Initial commit"
        }

        "log should traverse entire commit history chain" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create a chain of 10 commits
            for (i in 1..10) {
                val file = tmp.resolve("file$i.txt")
                Files.writeString(file, "content$i")
                val blob = FsObjectStore.writeBlob(repo, file)
                Index.update(repo, "file$i.txt", blob)
                CommitCmd { repo }.test(arrayOf("-m", "Commit $i"))
            }

            val cmd = LogCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0

            for (i in 1..10) {
                result.stdout shouldContain "Commit $i"
            }

            val commitCount = result.stdout.lines().count { it.startsWith("commit ") }
            commitCount shouldBe 10
        }

        "log should show branch references for commits" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create an initial commit
            val file1 = tmp.resolve("file1.txt")
            Files.writeString(file1, "content1")
            val blob1 = FsObjectStore.writeBlob(repo, file1)
            Index.update(repo, "file1.txt", blob1)
            CommitCmd { repo }.test(arrayOf("-m", "First commit"))

            BranchCmd { repo }.test(arrayOf("feature"))

            val cmd = LogCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0

            result.stdout shouldContain "(HEAD -> master, feature)"
        }

        "log should show HEAD -> branch for current branch" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create commit
            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blob = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blob)
            CommitCmd { repo }.test(arrayOf("-m", "Test commit"))

            val cmd = LogCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0

            result.stdout shouldContain "HEAD -> master"
        }

        "log should show branches after checkout" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create an initial commit
            val file1 = tmp.resolve("file1.txt")
            Files.writeString(file1, "content1")
            val blob1 = FsObjectStore.writeBlob(repo, file1)
            Index.update(repo, "file1.txt", blob1)
            CommitCmd { repo }.test(arrayOf("-m", "First commit"))

            CheckoutCmd { repo }.test(arrayOf("-b", "feature"))

            val file2 = tmp.resolve("file2.txt")
            Files.writeString(file2, "content2")
            val blob2 = FsObjectStore.writeBlob(repo, file2)
            Index.update(repo, "file2.txt", blob2)
            CommitCmd { repo }.test(arrayOf("-m", "Feature commit"))

            val cmd = LogCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0

            val firstCommitLine = result.stdout.lines().first { it.startsWith("commit ") }
            firstCommitLine shouldContain "HEAD -> feature"

            result.stdout shouldContain "(master)"
        }
    })
