package dev.kamisama.core.diff

import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Provides diff functionality for comparing blobs, trees, and commits.
 */
object Diff {
    /**
     * Represents a file change in a tree diff.
     */
    sealed class FileChange {
        data class Added(
            val path: String,
            val blobId: ObjectId,
        ) : FileChange()

        data class Deleted(
            val path: String,
            val blobId: ObjectId,
        ) : FileChange()

        data class Modified(
            val path: String,
            val oldBlobId: ObjectId,
            val newBlobId: ObjectId,
        ) : FileChange()
    }

    /**
     * Compare two blobs and return a unified diff.
     */
    fun diffBlobs(
        repo: RepoLayout,
        oldBlobId: ObjectId?,
        newBlobId: ObjectId?,
        path: String,
        contextLines: Int = 3,
    ): String {
        val oldContent = oldBlobId?.let { readBlobAsLines(repo, it) } ?: emptyList()
        val newContent = newBlobId?.let { readBlobAsLines(repo, it) } ?: emptyList()

        // Check if files are binary
        val oldBinary = oldBlobId?.let { isBinary(repo, it) } ?: false
        val newBinary = newBlobId?.let { isBinary(repo, it) } ?: false

        if (oldBinary || newBinary) {
            return buildString {
                append("diff --timetree a/$path b/$path\n")

                // Add the index line with abbreviated blob IDs (like git)
                val oldHash = oldBlobId?.toHex()?.take(7) ?: "0000000"
                val newHash = newBlobId?.toHex()?.take(7) ?: "0000000"
                append("index $oldHash..$newHash 100644\n")

                when {
                    oldBlobId == null -> append("new file\n")
                    newBlobId == null -> append("deleted file\n")
                }
                append("Binary files differ\n")
            }
        }

        val edits = Myers.computeEdits(oldContent, newContent)

        if (edits.isEmpty() || edits.all { it is Myers.Edit.Keep }) {
            return ""
        }

        val diff =
            Myers.formatUnifiedDiff(
                edits,
                aLabel = "a/$path",
                bLabel = "b/$path",
                contextLines = contextLines,
            )

        return buildString {
            append("diff --timetree a/$path b/$path\n")

            // Add the index line with abbreviated blob IDs (like git)
            val oldHash = oldBlobId?.toHex()?.take(7) ?: "0000000"
            val newHash = newBlobId?.toHex()?.take(7) ?: "0000000"
            append("index $oldHash..$newHash 100644\n")

            when {
                oldBlobId == null && newBlobId != null -> append("new file\n")
                oldBlobId != null && newBlobId == null -> append("deleted file\n")
            }
            append(diff)
        }
    }

    /**
     * Compare two trees and return a list of file changes.
     */
    fun diffTrees(
        repo: RepoLayout,
        oldTreeId: ObjectId?,
        newTreeId: ObjectId?,
    ): List<FileChange> {
        val oldEntries = oldTreeId?.let { parseTree(repo, it) } ?: emptyMap()
        val newEntries = newTreeId?.let { parseTree(repo, it) } ?: emptyMap()

        val allPaths = (oldEntries.keys + newEntries.keys).sorted()
        val changes = mutableListOf<FileChange>()

        for (path in allPaths) {
            val oldBlob = oldEntries[path]
            val newBlob = newEntries[path]

            when {
                oldBlob == null && newBlob != null -> {
                    changes.add(FileChange.Added(path, newBlob))
                }
                oldBlob != null && newBlob == null -> {
                    changes.add(FileChange.Deleted(path, oldBlob))
                }
                oldBlob != null && newBlob != null && oldBlob != newBlob -> {
                    changes.add(FileChange.Modified(path, oldBlob, newBlob))
                }
            }
        }

        return changes
    }

    /**
     * Compare two commits and return a unified diff of all changes.
     */
    fun diffCommits(
        repo: RepoLayout,
        oldCommitId: ObjectId?,
        newCommitId: ObjectId?,
        contextLines: Int = 3,
    ): String {
        val oldTreeId = oldCommitId?.let { readCommitTree(repo, it) }
        val newTreeId = newCommitId?.let { readCommitTree(repo, it) }

        val changes = diffTrees(repo, oldTreeId, newTreeId)

        if (changes.isEmpty()) {
            return ""
        }

        val result = StringBuilder()

        for (change in changes) {
            when (change) {
                is FileChange.Added -> {
                    val diff = diffBlobs(repo, null, change.blobId, change.path, contextLines)
                    result.append(diff)
                }
                is FileChange.Deleted -> {
                    val diff = diffBlobs(repo, change.blobId, null, change.path, contextLines)
                    result.append(diff)
                }
                is FileChange.Modified -> {
                    val diff =
                        diffBlobs(
                            repo,
                            change.oldBlobId,
                            change.newBlobId,
                            change.path,
                            contextLines,
                        )
                    result.append(diff)
                }
            }
        }

        return result.toString()
    }

    /**
     * Read a blob and return its contents as lines.
     */
    private fun readBlobAsLines(
        repo: RepoLayout,
        blobId: ObjectId,
    ): List<String> {
        val content = readBlob(repo, blobId)
        // Split by a new line, avoiding empty trailing line from lines()
        return if (content.isEmpty()) {
            emptyList()
        } else {
            content.split('\n')
        }
    }

    /**
     * Read a blob and return its raw content as a string.
     */
    private fun readBlob(
        repo: RepoLayout,
        blobId: ObjectId,
    ): String {
        val hex = blobId.toHex()
        val blobPath = repo.objects.resolve(hex.take(2)).resolve(hex.substring(2))

        if (!Files.exists(blobPath)) {
            throw IllegalArgumentException("Blob $hex not found")
        }

        return try {
            Files.readString(blobPath, StandardCharsets.UTF_8)
        } catch (e: java.nio.charset.MalformedInputException) {
            throw IllegalArgumentException("Blob $hex contains invalid UTF-8 data (possibly binary)")
        }
    }

    /**
     * Check if a blob contains binary data.
     */
    private fun isBinary(
        repo: RepoLayout,
        blobId: ObjectId,
    ): Boolean {
        val hex = blobId.toHex()
        val blobPath = repo.objects.resolve(hex.take(2)).resolve(hex.substring(2))

        if (!Files.exists(blobPath)) {
            return false
        }

        val bytes = Files.readAllBytes(blobPath)
        val checkSize = minOf(bytes.size, 8000)

        for (i in 0 until checkSize) {
            if (bytes[i] == 0.toByte()) {
                return true
            }
        }

        return false
    }

    /**
     * Parse a tree object and return a map of the path-> blob ID.
     */
    fun parseTree(
        repo: RepoLayout,
        treeId: ObjectId,
        prefix: String = "",
    ): Map<String, ObjectId> {
        val hex = treeId.toHex()
        val treePath = repo.objects.resolve(hex.take(2)).resolve(hex.substring(2))

        if (!Files.exists(treePath)) {
            throw IllegalArgumentException("Tree $hex not found")
        }

        val content = Files.readString(treePath, StandardCharsets.UTF_8)
        val result = mutableMapOf<String, ObjectId>()

        for (line in content.lines()) {
            if (line.isBlank()) continue

            val parts = line.split('\t', limit = 2)
            if (parts.size != 2) continue

            val (modeAndName, idHex) = parts
            val modeParts = modeAndName.split(' ', limit = 2)
            if (modeParts.size != 2) continue

            val (mode, name) = modeParts
            val id = ObjectId.fromHex(idHex.trim())
            val fullPath = if (prefix.isEmpty()) name else "$prefix/$name"

            if (mode == "040000") {
                // Directory - recurse
                result.putAll(parseTree(repo, id, fullPath))
            } else {
                // File
                result[fullPath] = id
            }
        }

        return result
    }

    /**
     * Read a commit and return its tree ID.
     */
    fun readCommitTree(
        repo: RepoLayout,
        commitId: ObjectId,
    ): ObjectId? {
        val hex = commitId.toHex()
        val commitPath = repo.objects.resolve(hex.take(2)).resolve(hex.substring(2))

        if (!Files.exists(commitPath)) {
            throw IllegalArgumentException("Commit $hex not found")
        }

        val content = Files.readString(commitPath, StandardCharsets.UTF_8)

        for (line in content.lines()) {
            if (line.startsWith("tree ")) {
                return ObjectId.fromHex(line.substring(5).trim())
            }
        }

        return null
    }
}
