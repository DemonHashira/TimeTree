package dev.kamisama.core.objects

import dev.kamisama.core.hash.ObjectId
import java.util.TreeMap

/**
 * Builds tree objects from file path->ID mappings.
 */
object TreeBuilder {
    // Tree node with directories and files.
    data class Node(
        val name: String,
        val dirs: MutableMap<String, Node> = TreeMap(),
        val files: MutableList<FileEntry> = mutableListOf(),
    )

    // File entry with mode and object ID.
    data class FileEntry(
        val name: String,
        val id: ObjectId,
        val mode: String,
    )

    // Builds a tree from flat path->ID map.
    fun build(
        entries: Map<String, ObjectId>,
        modeProvider: (String) -> String = { "100644" },
        writeTree: (ByteArray) -> ObjectId,
    ): ObjectId {
        val root = Node("")

        // Build a tree structure from flat paths
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

    // Recursively writes directory tree objects.
    private fun writeDir(
        node: Node,
        writeTree: (ByteArray) -> ObjectId,
    ): ObjectId {
        val filesPart =
            node.files.sortedBy { it.name }.joinToString("") { f ->
                "${f.mode} ${f.name}\t${f.id.toHex()}\n"
            }

        val dirsWithIds = node.dirs.mapValues { (_, child) -> writeDir(child, writeTree) }
        val dirsPart =
            dirsWithIds.entries.sortedBy { it.key }.joinToString("") { (name, id) ->
                "040000 $name\t${id.toHex()}\n"
            }

        val content = (filesPart + dirsPart).toByteArray()
        return writeTree(content)
    }
}
