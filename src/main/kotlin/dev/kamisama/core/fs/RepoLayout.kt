package dev.kamisama.core.fs

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class RepoLayout(
    val root: Path,
) {
    val meta: Path get() = root.resolve(META_DIR)
    val objects: Path get() = meta.resolve("objects")
    val refsHeads: Path get() = meta.resolve("refs/heads")
    val head: Path get() = meta.resolve("HEAD")

    companion object {
        const val META_DIR: String = ".timetree"

        fun fromWorkingDir(): RepoLayout = RepoLayout(Paths.get("").toAbsolutePath().normalize())
    }
}

fun isInitialized(repo: RepoLayout): Boolean = Files.isDirectory(repo.meta)

fun ensureInitialized(
    repo: RepoLayout,
    defaultBranch: String,
): Boolean {
    if (isInitialized(repo)) return false
    Files.createDirectories(repo.objects)
    Files.createDirectories(repo.refsHeads)
    AtomicFile(repo.head).writeUtf8("ref: refs/heads/$defaultBranch\n")
    return true
}
