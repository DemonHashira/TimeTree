package dev.kamisama.core.fs

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Data class representing the directory structure of a TimeTree repository.
 * Manages paths to all important repository directories and files.
 */
data class RepoLayout(
    // Root directory of the repository (typically the working directory)
    val root: Path,
) {
    // Computed property: metadata directory (.timetree) inside root
    val meta: Path get() = root.resolve(META_DIR)

    // Computed property: objects directory for storing file content blobs
    val objects: Path get() = meta.resolve("objects")

    // Computed property: directory containing branch reference files
    val refsHeads: Path get() = meta.resolve("refs/heads")

    // Computed property: HEAD file that points to the current branch
    val head: Path get() = meta.resolve("HEAD")

    companion object {
        // Name of the metadata directory
        const val META_DIR: String = ".timetree"

        /**
         * Creates a RepoLayout from the current working directory.
         */
        fun fromWorkingDir(): RepoLayout = RepoLayout(Paths.get("").toAbsolutePath().normalize())
    }
}

/**
 * Checks if a TimeTree repository is fully initialized.
 * Returns true only if .timetree is a directory AND all required components exist.
 */
fun isInitialized(repo: RepoLayout): Boolean =
    Files.isDirectory(repo.meta) &&
        Files.isDirectory(repo.objects) &&
        Files.isDirectory(repo.refsHeads) &&
        Files.exists(repo.head)

/**
 * Initializes a new TimeTree repository if it doesn't already exist.
 * Creates the necessary directory structure and initial HEAD file.
 * Repairs partially initialized repositories by creating missing components.
 */
fun ensureInitialized(
    repo: RepoLayout,
    defaultBranch: String,
): Boolean {
    // Track if this is a fresh initialization (not a repair)
    val isAlreadyInitialized = isInitialized(repo)

    // Create or repair directory structure
    Files.createDirectories(repo.objects)
    Files.createDirectories(repo.refsHeads)

    // Create a HEAD file if missing
    if (!Files.exists(repo.head)) {
        AtomicFile(repo.head).writeUtf8("ref: refs/heads/$defaultBranch\n")
    }

    // Return true only if this was a brand-new initialization
    return !isAlreadyInitialized
}
