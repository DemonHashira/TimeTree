package dev.kamisama.core.objects

import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import dev.kamisama.core.hash.Sha1
import dev.kamisama.core.hash.Sha1Like
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Stores, reads and retrieves blob objects in the filesystem.
 * Layout: .timetree/objects/<hh>/<rest>
 * Hash = SHA-1 over: "timetree:v1\0blob <size>\0" + content.
 */
object FsObjectStore {
    // single shared writer for bytes already in memory
    private fun writeObject(
        repo: RepoLayout,
        content: ByteArray,
        headerProvider: (Long) -> ByteArray,
    ): ObjectId {
        val size = content.size.toLong()
        val header = headerProvider(size)
        val hasher: Sha1Like = Sha1()
        hasher.update(header)
        hasher.update(content)
        val id = hasher.digest()

        val (dir, leaf) = dirAndLeaf(repo, id)
        if (!Files.isDirectory(dir)) Files.createDirectories(dir)
        val objPath = dir.resolve(leaf)
        if (!Files.exists(objPath)) {
            Files.newByteChannel(objPath, setOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)).use { ch ->
                ch.write(java.nio.ByteBuffer.wrap(content))
            }
        }
        return id
    }

    // streaming writer for large blobs (Path -> ObjectId), avoids loading a whole file in RAM
    private fun writeObjectStreaming(
        repo: RepoLayout,
        path: Path,
        headerProvider: (Long) -> ByteArray,
    ): ObjectId {
        val size = Files.size(path)
        val header = headerProvider(size)
        val hasher: Sha1Like = Sha1()
        hasher.update(header)
        Files.newInputStream(path).use { input ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                hasher.update(buf, 0, n)
            }
        }
        val id = hasher.digest()

        val (dir, leaf) = dirAndLeaf(repo, id)
        if (!Files.isDirectory(dir)) Files.createDirectories(dir)
        val objPath = dir.resolve(leaf)
        if (!Files.exists(objPath)) {
            // copy file bytes as-is into the object (idempotent if already present)
            Files.copy(path, objPath, StandardCopyOption.COPY_ATTRIBUTES)
        }
        return id
    }

    fun writeBlob(
        repo: RepoLayout,
        path: Path,
    ): ObjectId = writeObjectStreaming(repo, path, ObjectHeaders::blobHeader)

    fun writeBlob(
        repo: RepoLayout,
        content: ByteArray,
    ): ObjectId = writeObject(repo, content, ObjectHeaders::blobHeader)

    fun writeTree(
        repo: RepoLayout,
        content: ByteArray,
    ): ObjectId = writeObject(repo, content, ObjectHeaders::treeHeader)

    fun writeCommit(
        repo: RepoLayout,
        content: ByteArray,
    ): ObjectId = writeObject(repo, content, ObjectHeaders::commitHeader)

    /**
     * Converts object ID to sharded path structure.
     * Returns (objects/<hh>, "<rest>") where hh is the first 2 hex chars.
     */
    private fun dirAndLeaf(
        repo: RepoLayout,
        id: ObjectId,
    ): Pair<Path, String> {
        val hex = id.toHex()
        val dir = repo.objects.resolve(hex.take(2)) // First 2 chars as a directory
        val leaf = hex.substring(2) // Remaining 38 chars as filename
        return Pair(dir, leaf)
    }
}
