package dev.kamisama.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.refs.Refs
import java.nio.file.Files

class BranchCmd(
    private val repoProvider: () -> RepoLayout = RepoLayout::fromWorkingDir,
) : CliktCommand(name = "branch") {
    private val branchName by argument(name = "branch").optional()
    private val delete by option("-d", "--delete", help = "Delete a branch").flag(default = false)
    private val list by option("-l", "--list", help = "List all branches").flag(default = false)

    override fun help(context: Context) = "List, create, or delete branches"

    override fun run() {
        val repo = repoProvider()
        require(Files.isDirectory(repo.meta)) { "Not a TimeTree repository (no .timetree directory)" }

        when {
            // Delete branch
            delete && branchName != null -> {
                deleteBranch(repo, branchName!!)
            }
            // Create branch
            branchName != null -> {
                createBranch(repo, branchName!!)
            }
            // List branches
            else -> {
                listBranches(repo)
            }
        }
    }

    private fun listBranches(repo: RepoLayout) {
        val branches = Refs.listBranches(repo)

        if (branches.isEmpty()) {
            echo("No branches yet")
            return
        }

        val head = Refs.readHead(repo)
        val currentBranch = head.currentBranch()

        // Sort branches for consistent output
        branches.keys.sorted().forEach { branchName ->
            val prefix = if (branchName == currentBranch) "* " else "  "
            echo("$prefix$branchName")
        }
    }

    private fun createBranch(
        repo: RepoLayout,
        name: String,
    ) {
        // Validate branch name
        if (name.contains('/') || name.contains(' ') || name.isEmpty()) {
            echo("error: invalid branch name '$name'", err = true)
            throw ProgramResult(1)
        }

        // Check if branch already exists
        if (Refs.branchExists(repo, name)) {
            echo("error: branch '$name' already exists", err = true)
            throw ProgramResult(1)
        }

        // Check if we have any commits
        val head = Refs.readHead(repo)
        if (head.id == null) {
            echo("error: cannot create branch - no commits yet", err = true)
            throw ProgramResult(1)
        }

        // Create the branch
        try {
            Refs.createBranch(repo, name, head.id)
            echo("Created branch '$name'")
        } catch (e: Exception) {
            echo("error: ${e.message}", err = true)
            throw ProgramResult(1)
        }
    }

    private fun deleteBranch(
        repo: RepoLayout,
        name: String,
    ) {
        // Check if trying to delete the current branch
        val head = Refs.readHead(repo)
        if (head.currentBranch() == name) {
            echo("error: cannot delete the currently checked out branch '$name'", err = true)
            throw ProgramResult(1)
        }

        // Delete the branch
        if (Refs.deleteBranch(repo, name)) {
            echo("Deleted branch '$name'")
        } else {
            echo("error: branch '$name' not found", err = true)
            throw ProgramResult(1)
        }
    }
}
