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

class CommitCmdTest :
    StringSpec({

        "commit writes a first commit with a tree and updates the master ref" {
            // --- Arrange: temp repo + init ---
            val tmpRoot: Path = tempdir().toPath()
            val repo = RepoLayout(tmpRoot)
            ensureInitialized(repo, defaultBranch = "master")

            // Create one file in the worktree
            val file = repo.root.resolve("a.txt")
            Files.writeString(file, "v1\n")

            // Stage it via core
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "a.txt", blobId)

            // --- Act: run commit command ---
            val out = CommitCmd { repo }.test(arrayOf("-m", "c1")).stdout
            out.shouldContain("[master]") // minimal sanity: branch tag printed

            // --- Assert: branch ref exists and points to a 40-hex object id ---
            val branchRef = repo.refsHeads.resolve("master")
            Files.exists(branchRef) shouldBe true
            val commitHex = Files.readString(branchRef).trim()
            commitHex.length shouldBe 40

            // Load commit object and check structure
            val commitObj = repo.objects.resolve(commitHex.take(2)).resolve(commitHex.substring(2))
            Files.exists(commitObj) shouldBe true
            val commitBody = Files.readString(commitObj, StandardCharsets.UTF_8)

            // Has a tree header and our message; the first commit must not have a parent
            val treeHex =
                Regex("""(?m)^tree ([0-9a-f]{40})$""")
                    .find(commitBody)
                    ?.groupValues
                    ?.get(1)
                    ?: error("Commit missing 'tree' header:\n$commitBody")
            (commitBody.contains("\nparent ")) shouldBe false
            commitBody.shouldContain("\n\nc1\n")

            // Load the tree object and verify it lists our staged file and exact blob id
            val treeObj = repo.objects.resolve(treeHex.take(2)).resolve(treeHex.substring(2))
            Files.exists(treeObj) shouldBe true
            val treeBody = Files.readString(treeObj, StandardCharsets.UTF_8)
            treeBody.shouldContain("100644 a.txt\t${blobId.toHex()}\n")
        }

        "commit rejects duplicate commit when tree hasn't changed" {
            // --- Arrange: temp repo + init ---
            val tmpRoot: Path = tempdir().toPath()
            val repo = RepoLayout(tmpRoot)
            ensureInitialized(repo, defaultBranch = "master")

            // Create one file in the worktree
            val file = repo.root.resolve("a.txt")
            Files.writeString(file, "v1\n")

            // Stage it via core
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "a.txt", blobId)

            // --- Act: run commit command first time ---
            val out1 = CommitCmd { repo }.test(arrayOf("-m", "c1")).stdout
            out1.shouldContain("[master]")

            // Get the first commit ID
            val branchRef = repo.refsHeads.resolve("master")
            val firstCommitHex = Files.readString(branchRef).trim()

            // --- Act: run commit command second time with same staged content ---
            val result = CommitCmd { repo }.test(arrayOf("-m", "c2"))

            // --- Assert: second commit should fail ---
            result.statusCode shouldBe 1
            result.stderr.shouldContain("Nothing to commit - tree is identical to parent commit")

            // Verify the branch still points to the first commit (no new commit created)
            val currentCommitHex = Files.readString(branchRef).trim()
            currentCommitHex shouldBe firstCommitHex
        }

        "commit allows duplicate tree with --allow-empty flag" {
            // --- Arrange: temp repo + init ---
            val tmpRoot: Path = tempdir().toPath()
            val repo = RepoLayout(tmpRoot)
            ensureInitialized(repo, defaultBranch = "master")

            // Create one file in the worktree
            val file = repo.root.resolve("a.txt")
            Files.writeString(file, "v1\n")

            // Stage it via core
            val blobId = FsObjectStore.writeBlob(repo, file)
            Index.update(repo, "a.txt", blobId)

            // --- Act: run commit command first time ---
            val out1 = CommitCmd { repo }.test(arrayOf("-m", "c1")).stdout
            out1.shouldContain("[master]")

            // Get the first commit ID
            val branchRef = repo.refsHeads.resolve("master")
            val firstCommitHex = Files.readString(branchRef).trim()

            // --- Act: run commit command second time with --allow-empty ---
            val result = CommitCmd { repo }.test(arrayOf("-m", "c2", "--allow-empty"))

            // --- Assert: second commit should succeed ---
            result.statusCode shouldBe 0
            result.stdout.shouldContain("[master]")

            // Verify a new commit was created
            val secondCommitHex = Files.readString(branchRef).trim()
            secondCommitHex.length shouldBe 40
            (secondCommitHex != firstCommitHex) shouldBe true

            // Verify both commits point to the same tree
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

            // Both commits should reference the same tree
            firstTreeHex shouldBe secondTreeHex
        }
    })
