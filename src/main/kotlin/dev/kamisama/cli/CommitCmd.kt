package dev.kamisama.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import dev.kamisama.core.index.Index
import dev.kamisama.core.objects.CommitWriter
import dev.kamisama.core.objects.FsObjectStore
import dev.kamisama.core.objects.TreeBuilder
import dev.kamisama.core.refs.Refs
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class CommitCmd(
    private val repoProvider: () -> RepoLayout = RepoLayout::fromWorkingDir,
) : CliktCommand(name = "commit") {
    private val message by option("-m", "--message").required()
    private val allowEmpty by option("--allow-empty").flag(default = false)
    private val authorName by option("--author-name").default(System.getProperty("user.name", "User"))
    private val authorEmail by option("--author-email").default("v.logodazhki@outlook.com")

    override fun help(context: Context) = "Record a snapshot of the staged changes"

    override fun run() {
        val repo = repoProvider()
        require(Files.isDirectory(repo.meta)) { "Not a TimeTree repository (no .timetree directory)" }

        // 1) Load staged entries
        val staged = Index.load(repo)
        if (staged.isEmpty() && !allowEmpty) {
            echo("Nothing to commit. Use --allow-empty to create an empty commit.", err = true)
            throw ProgramResult(1)
        }

        // 2) Build TREE recursively from index
        val treeId =
            TreeBuilder.build(
                entries = staged,
                writeTree = { bytes -> FsObjectStore.writeTree(repo, bytes) },
            )

        // 3) Resolve HEAD / parent
        val head = Refs.readHead(repo)
        val parent = head.id

        // 3.5) Check if a tree has changed from the parent commit
        if (parent != null && !allowEmpty) {
            val parentTreeId = readTreeIdFromCommit(repo, parent)
            if (parentTreeId == treeId) {
                echo(
                    "Nothing to commit - tree is identical to parent commit. " +
                        "Use --allow-empty to create an empty commit.",
                    err = true,
                )
                throw ProgramResult(1)
            }
        }

        // 4) Write COMMIT
        val commitId =
            CommitWriter.write(
                tree = treeId,
                parent = parent,
                message = message,
                meta = CommitWriter.Meta(authorName = authorName, authorEmail = authorEmail),
                persist = { bytes -> FsObjectStore.writeCommit(repo, bytes) },
            )

        // 5) Update ref
        val branchRef = head.refPath ?: "refs/heads/master"
        Refs.ensureHeadOn(repo, branchRef) // in case HEAD was missing/detached on new repo
        Refs.updateRef(repo, branchRef, commitId.toHex())

        echo("[${branchRef.substringAfterLast('/')}] ${commitId.toHex().take(12)} ${message.lineSequence().first()}")
    }

    private fun readTreeIdFromCommit(
        repo: RepoLayout,
        commitId: ObjectId,
    ): ObjectId {
        val hex = commitId.toHex()
        val commitPath = repo.objects.resolve(hex.take(2)).resolve(hex.substring(2))
        val commitBody = Files.readString(commitPath, StandardCharsets.UTF_8)
        val treeHex =
            Regex("""(?m)^tree ([0-9a-f]{40})$""")
                .find(commitBody)
                ?.groupValues
                ?.get(1)
                ?: error("Commit ${commitId.toHex()} missing 'tree' header")
        return ObjectId.fromHex(treeHex)
    }
}
