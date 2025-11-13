package dev.kamisama.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.kamisama.cli.CliUtils
import dev.kamisama.cli.Color
import dev.kamisama.core.checkout.Checkout
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import dev.kamisama.core.refs.Refs

class CheckoutCmd(
    private val repoProvider: () -> RepoLayout = RepoLayout::fromWorkingDir,
) : CliktCommand(name = "checkout") {
    private val target by argument(name = "branch-or-commit", help = "Branch name or commit ID to checkout")
    private val createBranch by option("-b", help = "Create and checkout a new branch").flag(default = false)

    override fun help(context: Context) = "Switch branches or checkout commits"

    override fun run() {
        val repo = repoProvider()
        CliUtils.requireRepository(repo)

        if (createBranch) {
            createAndCheckoutBranch(repo, target)
        } else {
            checkout(repo, target)
        }
    }

    private fun checkout(
        repo: RepoLayout,
        target: String,
    ) {
        // Try as a branch name first
        if (Refs.branchExists(repo, target)) {
            checkoutBranch(repo, target)
            return
        }

        // Try as commit ID
        try {
            val commitId =
                if (target.length == 40) {
                    ObjectId.fromHex(target)
                } else {
                    // Try to find commit by abbreviated ID
                    findCommitByAbbrev(repo, target)
                }

            if (commitId != null) {
                checkoutCommit(repo, commitId)
                return
            }
        } catch (e: Exception) {
            // Not a valid commit ID
        }

        echo("${Color.red("error:")} pathspec '$target' did not match any branch or commit", err = true)
        throw ProgramResult(1)
    }

    private fun checkoutBranch(
        repo: RepoLayout,
        branchName: String,
    ) {
        val head = Refs.readHead(repo)
        if (head.currentBranch() == branchName) {
            echo("Already on '$branchName'")
            return
        }

        try {
            Checkout.checkoutBranch(repo, branchName)
            echo("Switched to branch '$branchName'")
        } catch (e: Exception) {
            echo("${Color.red("error:")} ${e.message}", err = true)
            throw ProgramResult(1)
        }
    }

    private fun checkoutCommit(
        repo: RepoLayout,
        commitId: ObjectId,
    ) {
        try {
            Checkout.checkoutCommit(repo, commitId)
            echo("HEAD is now at ${commitId.toHex().take(12)}")
            echo("Note: switching to '${commitId.toHex().take(12)}'.")
            echo("")
            echo("You are in 'detached HEAD' state. You can make commits, but they")
            echo("won't belong to any branch unless you create a new branch.")
        } catch (e: Exception) {
            echo("${Color.red("error:")} ${e.message}", err = true)
            throw ProgramResult(1)
        }
    }

    private fun createAndCheckoutBranch(
        repo: RepoLayout,
        branchName: String,
    ) {
        // Validate branch name
        if (!CliUtils.isValidBranchName(branchName)) {
            echo("${Color.red("error:")} invalid branch name '$branchName'", err = true)
            throw ProgramResult(1)
        }

        // Check if a branch already exists
        if (Refs.branchExists(repo, branchName)) {
            echo("${Color.red("error:")} branch '$branchName' already exists", err = true)
            throw ProgramResult(1)
        }

        // Check if we have any commits
        val head = Refs.readHead(repo)
        if (head.id == null) {
            echo("${Color.red("error:")} cannot create branch - no commits yet", err = true)
            throw ProgramResult(1)
        }

        try {
            // Create the branch
            Refs.createBranch(repo, branchName, head.id)

            // Checkout the new branch
            Refs.ensureHeadOn(repo, "refs/heads/$branchName")

            echo("Switched to a new branch '$branchName'")
        } catch (e: Exception) {
            echo("${Color.red("error:")} ${e.message}", err = true)
            throw ProgramResult(1)
        }
    }

    /**
     * Find a commit by abbreviated ID.
     */
    private fun findCommitByAbbrev(
        repo: RepoLayout,
        abbrev: String,
    ): ObjectId? {
        val branches = Refs.listBranches(repo)
        for ((_, commitId) in branches) {
            if (commitId.toHex().startsWith(abbrev)) {
                return commitId
            }
        }
        return null
    }
}
