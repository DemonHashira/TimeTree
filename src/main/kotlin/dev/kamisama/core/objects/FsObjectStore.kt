package dev.kamisama.core.objects

import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import dev.kamisama.core.hash.Sha1
import dev.kamisama.core.hash.Sha1Like
import java.io.File
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
    /** Hash + write a regular file as a blob. */
    fun writeBlob(
        repo: RepoLayout,
        file: File,
    ): ObjectId = writeBlob(repo, file.toPath())

    fun writeBlob(
        repo: RepoLayout,
        file: Path,
    ): ObjectId {
        require(Files.isRegularFile(file)) { "Not a regular file: $file" }

        val size = Files.size(file)
        val header = ObjectHeaders.blobHeader(size)

        // Hash header + file content to generate object ID
        val hasher: Sha1Like = Sha1()
        hasher.update(header)
        Files.newInputStream(file).use { ins ->
            val buf = ByteArray(8192)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                hasher.update(buf, 0, n)
            }
        }
        val id = hasher.digest()

        // Store object using a sharded directory structure
        val (dir, leaf) = dirAndLeaf(repo, id)
        if (!Files.isDirectory(dir)) Files.createDirectories(dir)

        val objPath = dir.resolve(leaf)
        if (!Files.exists(objPath)) {
            Files.copy(file, objPath, StandardCopyOption.REPLACE_EXISTING)
        }
        return id
    }

    /** Hash + write in-memory bytes as a blob. */
    fun writeBlob(
        repo: RepoLayout,
        content: ByteArray,
    ): ObjectId {
        // Generate object ID by hashing header + content
        val header = ObjectHeaders.blobHeader(content.size.toLong())
        val hasher: Sha1Like = Sha1()
        hasher.update(header)
        hasher.update(content)
        val id = hasher.digest()

        // Store in a sharded directory structure
        val (dir, leaf) = dirAndLeaf(repo, id)
        if (!Files.isDirectory(dir)) Files.createDirectories(dir)

        val objPath = dir.resolve(leaf)
        if (!Files.exists(objPath)) {
            Files.write(objPath, content, StandardOpenOption.CREATE_NEW)
        }
        return id
    }

    /** Read a blob's raw bytes by id. */
    fun readBlob(
        repo: RepoLayout,
        id: ObjectId,
    ): ByteArray {
        val (dir, leaf) = dirAndLeaf(repo, id)
        val objPath = dir.resolve(leaf)
        require(Files.exists(objPath)) { "Object not found: $id" }
        return Files.readAllBytes(objPath)
    }

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
