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
 * Checks if a TimeTree repository is already initialized.
 */
fun isInitialized(repo: RepoLayout): Boolean = Files.isDirectory(repo.meta)

/**
 * Initializes a new TimeTree repository if it doesn't already exist.
 * Creates the necessary directory structure and initial HEAD file.
 */
fun ensureInitialized(
    repo: RepoLayout,
    defaultBranch: String,
): Boolean {
    // Skip initialization if the repository already exists
    if (isInitialized(repo)) return false

    // Create a directory structure for storing objects (file content)
    Files.createDirectories(repo.objects)

    // Create a directory structure for storing branch references
    Files.createDirectories(repo.refsHeads)

    // Create a HEAD file pointing to the default branch
    // Format follows Git convention: "ref: refs/heads/branchname"
    AtomicFile(repo.head).writeUtf8("ref: refs/heads/$defaultBranch\n")

    // Return true to indicate a new repository was created
    return true
}
