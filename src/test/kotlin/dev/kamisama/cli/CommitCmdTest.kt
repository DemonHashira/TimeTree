package dev.kamisama.cli

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
    })
