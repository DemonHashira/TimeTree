package dev.kamisama.cli

import dev.kamisama.core.fs.RepoLayout
import java.nio.file.Files

/**
 * CLI helper utilities for validation and checks.
 */
object CliUtils {
    /** Ensures the repository is initialized or throws an error. */
    fun requireRepository(repo: RepoLayout) {
        require(Files.isDirectory(repo.meta)) { "Not a TimeTree repository (no .timetree directory)" }
    }

    /** Checks if the branch name follows naming rules (no slashes or spaces). */
    fun isValidBranchName(name: String): Boolean = name.isNotEmpty() && !name.contains('/') && !name.contains(' ')
}
