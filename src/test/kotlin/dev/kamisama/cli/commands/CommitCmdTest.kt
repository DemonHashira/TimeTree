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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Tests for commit creation. */
class CommitCmdTest :
    StringSpec({

        "commit writes a first commit with a tree and updates the master ref" {
            val tmpRoot: Path = tempdir().toPath()
            val repo = RepoLayout(tmpRoot)
            ensureInitialized(repo, defaultBranch = "master")

            val file = repo.root.resolve("a.txt")
            Files.writeString(file, "v1\n")

            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "a.txt", blobId)

            val out = CommitCmd { repo }.test(arrayOf("-m", "c1")).stdout
            out.shouldContain("[master]")

            val branchRef = repo.refsHeads.resolve("master")
            Files.exists(branchRef) shouldBe true
            val commitHex = Files.readString(branchRef).trim()
            commitHex.length shouldBe 40

            val commitObj = repo.objects.resolve(commitHex.take(2)).resolve(commitHex.substring(2))
            Files.exists(commitObj) shouldBe true
            val commitBody = Files.readString(commitObj, StandardCharsets.UTF_8)

            val treeHex =
                Regex("""(?m)^tree ([0-9a-f]{40})$""")
                    .find(commitBody)
                    ?.groupValues
                    ?.get(1)
                    ?: error("Commit missing 'tree' header:\n$commitBody")
            (commitBody.contains("\nparent ")) shouldBe false
            commitBody.shouldContain("\n\nc1\n")

            val treeObj = repo.objects.resolve(treeHex.take(2)).resolve(treeHex.substring(2))
            Files.exists(treeObj) shouldBe true
            val treeBody = Files.readString(treeObj, StandardCharsets.UTF_8)
            treeBody.shouldContain("100644 a.txt\t${blobId.toHex()}\n")
        }

        "commit rejects duplicate commit when tree hasn't changed" {
            val tmpRoot: Path = tempdir().toPath()
            val repo = RepoLayout(tmpRoot)
            ensureInitialized(repo, defaultBranch = "master")

            val file = repo.root.resolve("a.txt")
            Files.writeString(file, "v1\n")

            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "a.txt", blobId)

            val out1 = CommitCmd { repo }.test(arrayOf("-m", "c1")).stdout
            out1.shouldContain("[master]")

            val branchRef = repo.refsHeads.resolve("master")
            val firstCommitHex = Files.readString(branchRef).trim()

            val result = CommitCmd { repo }.test(arrayOf("-m", "c2"))

            result.statusCode shouldBe 1
            result.stderr.shouldContain("Nothing to commit - tree is identical to parent commit")

            val currentCommitHex = Files.readString(branchRef).trim()
            currentCommitHex shouldBe firstCommitHex
        }

        "commit allows duplicate tree with --allow-empty flag" {
            val tmpRoot: Path = tempdir().toPath()
            val repo = RepoLayout(tmpRoot)
            ensureInitialized(repo, defaultBranch = "master")

            val file = repo.root.resolve("a.txt")
            Files.writeString(file, "v1\n")

            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "a.txt", blobId)

            val out1 = CommitCmd { repo }.test(arrayOf("-m", "c1")).stdout
            out1.shouldContain("[master]")

            val branchRef = repo.refsHeads.resolve("master")
            val firstCommitHex = Files.readString(branchRef).trim()

            val result = CommitCmd { repo }.test(arrayOf("-m", "c2", "--allow-empty"))

            result.statusCode shouldBe 0
            result.stdout.shouldContain("[master]")

            val secondCommitHex = Files.readString(branchRef).trim()
            secondCommitHex.length shouldBe 40
            (secondCommitHex != firstCommitHex) shouldBe true

            val firstCommitObj = repo.objects.resolve(firstCommitHex.take(2)).resolve(firstCommitHex.substring(2))
            val firstCommitBody = Files.readString(firstCommitObj, StandardCharsets.UTF_8)
            val firstTreeHex =
                Regex("""(?m)^tree ([0-9a-f]{40})$""")
                    .find(firstCommitBody)
                    ?.groupValues
                    ?.get(1)
                    ?: error("First commit missing 'tree' header")

            val secondCommitObj = repo.objects.resolve(secondCommitHex.take(2)).resolve(secondCommitHex.substring(2))
            val secondCommitBody = Files.readString(secondCommitObj, StandardCharsets.UTF_8)
            val secondTreeHex =
                Regex("""(?m)^tree ([0-9a-f]{40})$""")
                    .find(secondCommitBody)
                    ?.groupValues
                    ?.get(1)
                    ?: error("Second commit missing 'tree' header")

            firstTreeHex shouldBe secondTreeHex
        }
    })
