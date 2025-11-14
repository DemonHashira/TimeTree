package dev.kamisama.core.objects

import java.nio.charset.StandardCharsets

enum class ObjectType { BLOB, TREE, COMMIT }

/**
 * Generates namespaced object headers for domain separation from Git.
 */
object ObjectHeaders {
    private const val NS = "timetree:v1"

    fun header(
        type: ObjectType,
        size: Long,
    ): ByteArray = "$NS\u0000${type.name.lowercase()} $size\u0000".toByteArray(StandardCharsets.UTF_8)

    fun blobHeader(size: Long): ByteArray = header(ObjectType.BLOB, size)

    fun treeHeader(size: Long): ByteArray = header(ObjectType.TREE, size)

    fun commitHeader(size: Long): ByteArray = header(ObjectType.COMMIT, size)
}
