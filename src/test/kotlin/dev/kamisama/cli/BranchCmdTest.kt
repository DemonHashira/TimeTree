package dev.kamisama.cli

import com.github.ajalt.clikt.testing.test
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.fs.ensureInitialized
import dev.kamisama.core.index.Index
import dev.kamisama.core.objects.FsObjectStore
import dev.kamisama.core.refs.Refs
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

class BranchCmdTest :
    StringSpec({

        "branch should list no branches on fresh repository" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val cmd = BranchCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "No branches yet"
        }

        "branch should list current branch with asterisk" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create a commit so the branch exists
            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "initial"))

            val cmd = BranchCmd { repo }
            val result = cmd.test(emptyArray())

            result.statusCode shouldBe 0
            result.stdout shouldContain "* master"
        }

        "branch should create new branch from HEAD" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create an initial commit
            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "initial"))

            // Create a new branch
            val cmd = BranchCmd { repo }
            val result = cmd.test(arrayOf("feature"))

            result.statusCode shouldBe 0
            result.stdout shouldContain "Created branch 'feature'"

            // Verify branch exists
            Refs.branchExists(repo, "feature") shouldBe true
        }

        "branch should fail to create duplicate branch" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create an initial commit
            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "initial"))

            // Create branch
            BranchCmd { repo }.test(arrayOf("feature"))

            // Try to create the same branch again
            val result = BranchCmd { repo }.test(arrayOf("feature"))

            result.statusCode shouldBe 1
            result.stderr shouldContain "already exists"
        }

        "branch should delete existing branch" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create an initial commit
            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "initial"))

            // Create and delete branch
            BranchCmd { repo }.test(arrayOf("feature"))
            val result = BranchCmd { repo }.test(arrayOf("-d", "feature"))

            result.statusCode shouldBe 0
            result.stdout shouldContain "Deleted branch 'feature'"

            // Verify the branch is gone
            Refs.branchExists(repo, "feature") shouldBe false
        }

        "branch should not delete current branch" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create an initial commit
            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "initial"))

            // Try to delete the current branch
            val result = BranchCmd { repo }.test(arrayOf("-d", "master"))

            result.statusCode shouldBe 1
            result.stderr shouldContain "cannot delete the currently checked out branch"
        }

        "branch should list multiple branches in sorted order" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create an initial commit
            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "initial"))

            // Create multiple branches
            BranchCmd { repo }.test(arrayOf("zebra"))
            BranchCmd { repo }.test(arrayOf("alpha"))
            BranchCmd { repo }.test(arrayOf("beta"))

            val result = BranchCmd { repo }.test(emptyArray())

            result.statusCode shouldBe 0

            // Should be sorted alphabetically
            val lines =
                result.stdout.lines().filter {
                    it.contains("alpha") || it.contains("beta") ||
                        it.contains("zebra")
                }
            val alphaIdx = lines.indexOfFirst { it.contains("alpha") }
            val betaIdx = lines.indexOfFirst { it.contains("beta") }
            val zebraIdx = lines.indexOfFirst { it.contains("zebra") }

            (betaIdx in (alphaIdx + 1)..<zebraIdx) shouldBe true
        }
    })
