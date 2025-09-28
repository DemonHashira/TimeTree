package dev.kamisama.core.index

import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Manages the repository index - a mapping of file paths to their object IDs.
 */
object Index {
    private const val FILE_NAME = "index"

    /**
     * Updates or adds a file entry in the index with its object ID.
     */
    fun update(
        repo: RepoLayout,
        path: String,
        id: ObjectId,
    ) {
        val map = load(repo)
        map[path] = id
        save(repo, map)
    }

    private fun indexPath(repo: RepoLayout): Path = repo.meta.resolve(FILE_NAME)

    /**
     * Loads index entries from disk. Returns empty map if index doesn't exist.
     */
    private fun load(repo: RepoLayout): MutableMap<String, ObjectId> {
        val p = indexPath(repo)
        if (!Files.exists(p)) return linkedMapOf()
        val out = linkedMapOf<String, ObjectId>()
        Files.readAllLines(p, StandardCharsets.UTF_8).forEach { line ->
            if (line.isBlank()) return@forEach
            val sp = line.indexOf(' ')
            require(sp > 0) { "Corrupt index line: $line" }
            val hex = line.take(sp) // Object ID in hex
            val filePath = line.substring(sp + 1) // File path
            out[filePath] = ObjectId.fromHex(hex)
        }
        return out
    }

    /**
     * Persists index entries to disk in "objectid filepath" format.
     */
    private fun save(
        repo: RepoLayout,
        entries: Map<String, ObjectId>,
    ) {
        val p = indexPath(repo)
        if (!Files.isDirectory(p.parent)) Files.createDirectories(p.parent)
        val sb = StringBuilder(entries.size * 60) // Estimate capacity
        for ((path, id) in entries) {
            sb
                .append(id.toHex())
                .append(' ')
                .append(path)
                .append('\n')
        }
        Files.writeString(
            p,
            sb.toString(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }
}
