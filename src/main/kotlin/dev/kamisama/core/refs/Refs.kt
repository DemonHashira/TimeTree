package dev.kamisama.core.refs

import dev.kamisama.core.fs.AtomicFile
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import java.nio.charset.StandardCharsets
import java.nio.file.Files

object Refs {
    data class Head(
        val refPath: String?,
        val id: ObjectId?,
    ) // refPath null => detached

    /** Read HEAD; if it's a symbolic ref, also read the current tip id (if any). */
    fun readHead(repo: RepoLayout): Head {
        if (!Files.exists(repo.head)) return Head(refPath = "refs/heads/master", id = null)
        val s = Files.readString(repo.head, StandardCharsets.UTF_8).trim()
        return if (s.startsWith("ref: ")) {
            val ref = s.removePrefix("ref: ").trim()
            val p = repo.meta.resolve(ref)
            val id = if (Files.exists(p)) ObjectId.fromHex(Files.readString(p).trim()) else null
            Head(refPath = ref, id = id)
        } else {
            Head(refPath = null, id = ObjectId.fromHex(s))
        }
    }

    /** Ensure HEAD points to the given branch ref (used on the first commit in fresh repo). */
    fun ensureHeadOn(
        repo: RepoLayout,
        branchRef: String = "refs/heads/master",
    ) {
        AtomicFile(repo.head).writeUtf8("ref: $branchRef\n")
    }

    /** Update a ref atomically to a new commit id. */
    fun updateRef(
        repo: RepoLayout,
        refPath: String,
        idHex: String,
    ) {
        val p = repo.meta.resolve(refPath)
        if (!Files.isDirectory(p.parent)) Files.createDirectories(p.parent)
        AtomicFile(p).writeUtf8("$idHex\n")
    }
}
