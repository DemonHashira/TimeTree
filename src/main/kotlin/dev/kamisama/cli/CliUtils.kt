package dev.kamisama.cli

import dev.kamisama.core.fs.RepoLayout
import java.nio.file.Files

/**
 * Utility functions.
 */
object CliUtils {
    /**
     * Validates that the given repository layout represents a valid TimeTree repository.
     */
    fun requireRepository(repo: RepoLayout) {
        require(Files.isDirectory(repo.meta)) { "Not a TimeTree repository (no .timetree directory)" }
    }

    /**
     * Validates a branch name according to TimeTree rules.
     */
    fun isValidBranchName(name: String): Boolean = name.isNotEmpty() && !name.contains('/') && !name.contains(' ')
}
