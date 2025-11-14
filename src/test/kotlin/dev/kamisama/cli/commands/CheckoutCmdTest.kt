package dev.kamisama.cli.commands

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

/** Tests for branch switching. */
class CheckoutCmdTest :
    StringSpec({

        "checkout should switch to existing branch" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val file1 = tmp.resolve("file1.txt")
            Files.writeString(file1, "content1")
            val blob1 = FsObjectStore.writeBlob(repo, file1)
            Index.update(repo, "file1.txt", blob1)
            CommitCmd { repo }.test(arrayOf("-m", "initial"))

            BranchCmd { repo }.test(arrayOf("feature"))
            val result = CheckoutCmd { repo }.test(arrayOf("feature"))

            result.statusCode shouldBe 0
            result.stdout shouldContain "Switched to branch 'feature'"

            val head = Refs.readHead(repo)
            head.currentBranch() shouldBe "feature"
        }

        "checkout should say already on branch if no change" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "initial"))

            val result = CheckoutCmd { repo }.test(arrayOf("master"))

            result.statusCode shouldBe 0
            result.stdout shouldContain "Already on 'master'"
        }

        "checkout -b should create and switch to new branch" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "initial"))

            val result = CheckoutCmd { repo }.test(arrayOf("-b", "newbranch"))

            result.statusCode shouldBe 0
            result.stdout shouldContain "Switched to a new branch 'newbranch'"

            Refs.branchExists(repo, "newbranch") shouldBe true
            Refs.readHead(repo).currentBranch() shouldBe "newbranch"
        }

        "checkout should fail for non-existent branch" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blobId)
            CommitCmd { repo }.test(arrayOf("-m", "initial"))

            val result = CheckoutCmd { repo }.test(arrayOf("nonexistent"))

            result.statusCode shouldBe 1
            result.stderr shouldContain "did not match any branch or commit"
        }

        "checkout should update working tree files" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create an initial commit on master
            val file = tmp.resolve("test.txt")
            Files.writeString(file, "master content")
            val blob1 = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blob1)
            CommitCmd { repo }.test(arrayOf("-m", "master commit"))

            // Create a feature branch and add different content
            BranchCmd { repo }.test(arrayOf("feature"))
            CheckoutCmd { repo }.test(arrayOf("feature"))

            Files.writeString(file, "feature content")
            val blob2 = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blob2)
            CommitCmd { repo }.test(arrayOf("-m", "feature commit"))

            // Switch back to master
            CheckoutCmd { repo }.test(arrayOf("master"))

            // Verify file content is from master
            val content = Files.readString(file)
            content shouldBe "master content"
        }

        "checkout should update index to match target branch" {
            val tmp = tempdir().toPath()
            val repo = RepoLayout(tmp)
            ensureInitialized(repo, "master")

            // Create an initial commit on master
            val file = tmp.resolve("test.txt")
            Files.writeString(file, "content")
            val blob = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "test.txt", blob)
            CommitCmd { repo }.test(arrayOf("-m", "initial"))

            // Create a feature branch with a new file
            BranchCmd { repo }.test(arrayOf("feature"))
            CheckoutCmd { repo }.test(arrayOf("feature"))

            val newFile = tmp.resolve("feature.txt")
            Files.writeString(newFile, "feature content")
            val featureBlob = FsObjectStore.writeBlob(repo, newFile)
            Index.update(repo, "feature.txt", featureBlob)
            CommitCmd { repo }.test(arrayOf("-m", "feature commit"))

            // Switch back to master
            CheckoutCmd { repo }.test(arrayOf("master"))

            // Verify index doesn't have feature.txt
            val index = Index.load(repo)
            index.containsKey("feature.txt") shouldBe false
            index.containsKey("test.txt") shouldBe true
        }
    })
