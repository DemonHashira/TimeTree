package dev.kamisama.core.fs

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Repository directory structure and path management.
 */
data class RepoLayout(
    val root: Path,
) {
    val meta: Path get() = root.resolve(META_DIR)
    val objects: Path get() = meta.resolve("objects")
    val refsHeads: Path get() = meta.resolve("refs/heads")
    val head: Path get() = meta.resolve("HEAD")

    /** Returns normalized absolute paths for root and meta. */
    fun normalizedPaths(): Pair<Path, Path> {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val normalizedMeta = normalizedRoot.resolve(META_DIR)
        return normalizedRoot to normalizedMeta
    }

    companion object {
        const val META_DIR: String = ".timetree"

        fun fromWorkingDir(): RepoLayout = RepoLayout(Paths.get("").toAbsolutePath().normalize())
    }
}

/** Checks if the repository is fully initialized. */
fun isInitialized(repo: RepoLayout): Boolean =
    Files.isDirectory(repo.meta) &&
        Files.isDirectory(repo.objects) &&
        Files.isDirectory(repo.refsHeads) &&
        Files.exists(repo.head)

/** Initializes or repairs the repository structure. */
fun ensureInitialized(
    repo: RepoLayout,
    defaultBranch: String,
): Boolean {
    val isAlreadyInitialized = isInitialized(repo)

    Files.createDirectories(repo.objects)
    Files.createDirectories(repo.refsHeads)

    if (!Files.exists(repo.head)) {
        AtomicFile(repo.head).writeUtf8("ref: refs/heads/$defaultBranch\n")
    }

    return !isAlreadyInitialized
}
