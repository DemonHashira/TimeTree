package dev.kamisama.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import dev.kamisama.cli.CliUtils
import dev.kamisama.core.diff.Diff
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.index.Index
import dev.kamisama.core.objects.CommitWriter
import dev.kamisama.core.objects.FsObjectStore
import dev.kamisama.core.objects.TreeBuilder
import dev.kamisama.core.refs.Refs

class CommitCmd(
    private val repoProvider: () -> RepoLayout = RepoLayout::fromWorkingDir,
) : CliktCommand(name = "commit") {
    private val message by option("-m", "--message").required()
    private val allowEmpty by option("--allow-empty").flag(default = false)
    private val authorName by option("--author-name").default(System.getProperty("user.name", "User"))
    private val authorEmail by option("--author-email").default("v.logodazhki@outlook.com")

    override fun help(context: Context) = "Save staged changes to repository"

    override fun run() {
        val repo = repoProvider()
        CliUtils.requireRepository(repo)

        // Load staged entries
        val staged = Index.load(repo)
        if (staged.isEmpty() && !allowEmpty) {
            echo("Nothing to commit. Use --allow-empty to create an empty commit.", err = true)
            throw ProgramResult(1)
        }

        // Build tree recursively from the index
        val treeId =
            TreeBuilder.build(
                entries = staged,
                writeTree = { bytes -> FsObjectStore.writeTree(repo, bytes) },
            )

        // Resolve HEAD / parent
        val head = Refs.readHead(repo)
        val parent = head.id

        // Check if a tree has changed from the parent commit
        if (parent != null && !allowEmpty) {
            val parentTreeId = Diff.readCommitTree(repo, parent)
            if (parentTreeId == null || parentTreeId == treeId) {
                echo(
                    "Nothing to commit - tree is identical to parent commit. " +
                        "Use --allow-empty to create an empty commit.",
                    err = true,
                )
                throw ProgramResult(1)
            }
        }

        // Write commit object
        val commitId =
            CommitWriter.write(
                tree = treeId,
                parent = parent,
                message = message,
                meta = CommitWriter.Meta(authorName = authorName, authorEmail = authorEmail),
                persist = { bytes -> FsObjectStore.writeCommit(repo, bytes) },
            )

        // Update ref
        val branchRef = head.refPath ?: "refs/heads/master"
        Refs.ensureHeadOn(repo, branchRef)
        Refs.updateRef(repo, branchRef, commitId.toHex())

        val branchName = branchRef.substringAfterLast('/')
        val commitMessage = message.lineSequence().first()
        echo(
            "[$branchName] ${commitId.toHex().take(12)} $commitMessage",
        )
    }
}
