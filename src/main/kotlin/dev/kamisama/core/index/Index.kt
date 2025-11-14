package dev.kamisama.core.index

import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Collections

/**
 * Manages repository index mapping file paths to object IDs.
 */
object Index {
    private const val FILE_NAME = "index"

    /** Returns immutable view of index entries. */
    fun load(repo: RepoLayout): Map<String, ObjectId> = Collections.unmodifiableMap(loadMutable(repo))

    /** Loads mutable index for update operations. */
    internal fun loadMutable(repo: RepoLayout): MutableMap<String, ObjectId> {
        val p = indexPath(repo)
        if (!Files.exists(p)) return linkedMapOf()
        val out = linkedMapOf<String, ObjectId>()
        Files.readAllLines(p, StandardCharsets.UTF_8).forEach { raw ->
            val line = raw.trimEnd('\r', '\n')
            if (line.isEmpty()) return@forEach
            val sp = line.indexOf(' ')
            require(sp in 1 until line.length - 1) { "Corrupt index line: $line" }
            val hex = line.take(sp)
            require(hex.length == 40) { "Bad object id length in index: $hex" }
            val filePath = line.substring(sp + 1)
            out[filePath] = ObjectId.fromHex(hex)
        }
        return out
    }

    /** Atomically adds or updates a single index entry. */
    fun update(
        repo: RepoLayout,
        path: String,
        id: ObjectId,
    ) {
        val map = loadMutable(repo)
        map[path] = id
        save(repo, map)
    }

    private fun indexPath(repo: RepoLayout): Path = repo.meta.resolve(FILE_NAME)

    /** Persists entries atomically in sorted order. */
    private fun save(
        repo: RepoLayout,
        entries: Map<String, ObjectId>,
    ) {
        val p = indexPath(repo)
        if (!Files.isDirectory(p.parent)) Files.createDirectories(p.parent)

        val sb = StringBuilder(entries.size * 64)
        entries.keys.sorted().forEach { path ->
            sb
                .append(entries.getValue(path).toHex())
                .append(' ')
                .append(path)
                .append('\n')
        }

        val tmp = p.resolveSibling("${p.fileName}.tmp")
        Files.writeString(
            tmp,
            sb.toString(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
