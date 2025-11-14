package dev.kamisama.core.objects

import dev.kamisama.core.hash.ObjectId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CommitWriter {
    data class Meta(
        val authorName: String = System.getProperty("user.name", "User"),
        val authorEmail: String = "user@example.com",
        val whenEpochSeconds: Long = Instant.now().epochSecond,
        val timezone: String = getLocalTimezone(),
    ) {
        companion object {
            /** Gets local timezone offset in Git format (e.g., "+0300"). */
            private fun getLocalTimezone(): String {
                val now = Instant.now()
                val zoneId = ZoneId.systemDefault()
                val zonedDateTime = now.atZone(zoneId)
                val formatter = DateTimeFormatter.ofPattern("Z")
                return zonedDateTime.format(formatter)
            }
        }
    }

    /** Serializes commits with metadata and returns object ID. */
    fun write(
        tree: ObjectId,
        parent: ObjectId?,
        message: String,
        meta: Meta = Meta(),
        persist: (ByteArray) -> ObjectId,
    ): ObjectId {
        val a = "${meta.authorName} <${meta.authorEmail}> ${meta.whenEpochSeconds} ${meta.timezone}"
        val sb =
            StringBuilder(256)
                .append("tree ")
                .append(tree.toHex())
                .append('\n')
        if (parent != null) sb.append("parent ").append(parent.toHex()).append('\n')
        sb.append("author ").append(a).append('\n')
        sb.append("committer ").append(a).append('\n')
        sb.append('\n')
        sb.append(message.trimEnd()).append('\n')
        return persist(sb.toString().toByteArray())
    }
}
