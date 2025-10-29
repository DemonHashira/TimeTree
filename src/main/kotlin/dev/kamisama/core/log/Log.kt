package dev.kamisama.core.log

import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import dev.kamisama.core.refs.Refs
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Represents a commit entry in the log.
 */
data class CommitEntry(
    val id: ObjectId,
    val tree: ObjectId,
    val parent: ObjectId?,
    val author: String,
    val authorEmail: String,
    val timestamp: Long,
    val timezone: String,
    val message: String,
) {
    /**
     * Returns abbreviated commit ID (first 12 characters).
     */
    fun abbreviatedId(): String = id.toHex()

    /**
     * Returns a formatted timestamp.
     */
    fun formattedTimestamp(): String {
        val instant = Instant.ofEpochSecond(timestamp)

        // Parse timezone
        val zoneOffset =
            try {
                ZoneOffset.of(timezone)
            } catch (e: Exception) {
                ZoneOffset.UTC
            }

        val zonedDateTime = instant.atZone(zoneOffset)
        val formatter = DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy Z")
        return zonedDateTime.format(formatter)
    }

    /**
     * Returns the first line of the commit message.
     */
    fun firstLineOfMessage(): String = message.lines().firstOrNull()?.trim() ?: ""
}

/**
 * Provides commit history traversal functionality.
 */
object Log {
    /**
     * Retrieves commit history starting from HEAD in reverse chronological order.
     * Walks the parent chain from the current branch tip.
     */
    fun getHistory(
        repo: RepoLayout,
        maxCount: Int? = null,
        all: Boolean = false,
    ): List<CommitEntry> {
        if (all) {
            return getAllHistory(repo, maxCount)
        }

        val head = Refs.readHead(repo)
        val startCommitId = head.id ?: return emptyList()

        val commits = mutableListOf<CommitEntry>()
        var currentId: ObjectId? = startCommitId
        var count = 0

        while (currentId != null) {
            // Check if we've reached the max count
            if (maxCount != null && count >= maxCount) {
                break
            }

            // Read and parse the commit
            val commit = readCommit(repo, currentId) ?: break
            commits.add(commit)

            // Move to parent commit
            currentId = commit.parent
            count++
        }

        return commits
    }

    /**
     * Retrieves all commits from all branches in the repository.
     */
    private fun getAllHistory(
        repo: RepoLayout,
        maxCount: Int? = null,
    ): List<CommitEntry> {
        val branches = Refs.listBranches(repo)
        val visited = mutableSetOf<ObjectId>()
        val commits = mutableListOf<CommitEntry>()

        // Start from all branch tips
        for ((_, commitId) in branches) {
            var currentId: ObjectId? = commitId

            while (currentId != null && currentId !in visited) {
                visited.add(currentId)

                // Read and parse the commit
                val commit = readCommit(repo, currentId) ?: break
                commits.add(commit)

                // Move to parent commit
                currentId = commit.parent
            }
        }

        // Sort commits by timestamp (most recent first)
        val sortedCommits = commits.sortedByDescending { it.timestamp }

        // Apply max count limit if specified
        return if (maxCount != null && maxCount > 0) {
            sortedCommits.take(maxCount)
        } else {
            sortedCommits
        }
    }

    /**
     * Reads and parses a commit object from the object store.
     */
    fun readCommit(
        repo: RepoLayout,
        commitId: ObjectId,
    ): CommitEntry? {
        val hex = commitId.toHex()
        val commitPath = repo.objects.resolve(hex.take(2)).resolve(hex.substring(2))

        if (!Files.exists(commitPath)) {
            return null
        }

        val content = Files.readString(commitPath, StandardCharsets.UTF_8)
        return parseCommit(commitId, content)
    }

    /**
     * Parses commit object content into a CommitEntry.
     */
    private fun parseCommit(
        id: ObjectId,
        content: String,
    ): CommitEntry? {
        val lines = content.lines()
        var tree: ObjectId? = null
        var parent: ObjectId? = null
        var author = ""
        var authorEmail = ""
        var timestamp = 0L
        var timezone = "+0000"
        val messageLines = mutableListOf<String>()
        var inMessage = false

        for (line in lines) {
            when {
                inMessage -> {
                    messageLines.add(line)
                }
                line.startsWith("tree ") -> {
                    tree = ObjectId.fromHex(line.substring(5).trim())
                }
                line.startsWith("parent ") -> {
                    parent = ObjectId.fromHex(line.substring(7).trim())
                }
                line.startsWith("author ") -> {
                    val authorLine = line.substring(7).trim()
                    val parsed = parseAuthorLine(authorLine)
                    author = parsed.name
                    authorEmail = parsed.email
                    timestamp = parsed.timestamp
                    timezone = parsed.timezone
                }
                line.isBlank() -> {
                    inMessage = true
                }
            }
        }

        if (tree == null) {
            return null
        }

        val message = messageLines.joinToString("\n").trim()

        return CommitEntry(
            id = id,
            tree = tree,
            parent = parent,
            author = author,
            authorEmail = authorEmail,
            timestamp = timestamp,
            timezone = timezone,
            message = message,
        )
    }

    /**
     * Parses author/committer line.
     */
    private fun parseAuthorLine(line: String): AuthorInfo {
        // Extract email
        val emailStart = line.indexOf('<')
        val emailEnd = line.indexOf('>')

        if (emailStart == -1 || emailEnd == -1) {
            return AuthorInfo("Unknown", "unknown@example.com", 0L, "+0000")
        }

        val name = line.take(emailStart).trim()
        val email = line.substring(emailStart + 1, emailEnd).trim()

        // Extract timestamp and timezone
        val afterEmail = line.substring(emailEnd + 1).trim()
        val parts = afterEmail.split(" ")

        val timestamp = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val timezone = parts.getOrNull(1) ?: "+0000"

        return AuthorInfo(name, email, timestamp, timezone)
    }

    /**
     * Data class representing parsed author information.
     */
    private data class AuthorInfo(
        val name: String,
        val email: String,
        val timestamp: Long,
        val timezone: String,
    )
}
