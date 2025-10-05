package dev.kamisama.core.objects

import dev.kamisama.core.hash.ObjectId
import java.util.TreeMap

/**
 * Serialize a TREE object from {path -> blobId} entries.
 * Format per entry (one per line), sorted by name within each directory:
 *   - file:  "<mode> <name>\t<hex-id>\n"   (mode: 100644 or 100755)
 *   - dir:   "040000 <name>\t<hex-id>\n"  (hex-id is the child tree id)
 */
object TreeBuilder {
    data class Node(
        val name: String,
        val dirs: MutableMap<String, Node> = TreeMap(),
        val files: MutableList<FileEntry> = mutableListOf(),
    )

    data class FileEntry(
        val name: String,
        val id: ObjectId,
        val mode: String,
    )

    fun build(
        entries: Map<String, ObjectId>,
        modeProvider: (String) -> String = { "100644" },
        writeTree: (ByteArray) -> ObjectId,
    ): ObjectId {
        val root = Node("")
        for ((path, id) in entries) {
            val pathSegments = path.split('/').filter { it.isNotEmpty() }
            var cur = root
            for (i in 0 until pathSegments.size - 1) {
                cur = cur.dirs.computeIfAbsent(pathSegments[i]) { Node(it) }
            }
            val leaf = pathSegments.last()
            cur.files += FileEntry(leaf, id, modeProvider(path))
        }
        return writeDir(root, writeTree)
    }

    private fun writeDir(
        node: Node,
        writeTree: (ByteArray) -> ObjectId,
    ): ObjectId {
        // Files sorted by name
        val filesPart =
            node.files.sortedBy { it.name }.joinToString("") { f ->
                "${f.mode} ${f.name}\t${f.id.toHex()}\n"
            }
        // Recurse into dirs (TreeMap keeps keys sorted)
        val dirsWithIds = node.dirs.mapValues { (_, child) -> writeDir(child, writeTree) }
        val dirsPart =
            dirsWithIds.entries.sortedBy { it.key }.joinToString("") { (name, id) ->
                "040000 $name\t${id.toHex()}\n"
            }
        val content = (filesPart + dirsPart).toByteArray()
        return writeTree(content)
    }
}
