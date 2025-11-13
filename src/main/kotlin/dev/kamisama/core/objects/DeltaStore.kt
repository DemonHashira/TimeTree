package dev.kamisama.core.objects

import dev.kamisama.core.delta.io.DeltaIO
import dev.kamisama.core.delta.RsyncDelta
import dev.kamisama.core.fs.AtomicFile
import dev.kamisama.core.fs.RepoLayout
import dev.kamisama.core.hash.ObjectId
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Delta-aware blob storage that keeps OIDs content-addressed.
 */
object DeltaStore {
    private const val MIN_DELTA_FILE_SIZE = 64 * 1024
    private const val MAX_DELTA_RATIO = 0.70
    private const val MAX_CHAIN_DEPTH = 20
    private const val DEFAULT_BLOCK_SIZE = 8192

    private val TTDL_MAGIC =
        byteArrayOf('T'.code.toByte(), 'T'.code.toByte(), 'D'.code.toByte(), 'L'.code.toByte(), 0x01)
    private val deltaAlgo = RsyncDelta()

    data class BlobMetadata(
        val isStoredAsDelta: Boolean,
        val chainDepth: Int = 0,
        val baseOid: ObjectId? = null,
    )

    /**
     * Stores @path. If a suitable base is provided and delta is beneficial, stores delta payload.
     */
    fun storeBlobWithDelta(
        repo: RepoLayout,
        path: Path,
        recentBlobId: ObjectId? = null,
    ): Pair<ObjectId, Boolean> {
        val fileSize = Files.size(path)
        val contentOid = FsObjectStore.computeBlobHash(path)

        // If an object already exists, keep its storage format and fix metadata if needed.
        val objPath = objectPath(repo, contentOid)
        if (Files.exists(objPath)) {
            val existingMeta = getMetadata(repo, contentOid)
            if (existingMeta != null) return contentOid to existingMeta.isStoredAsDelta

            if (looksLikeDeltaObject(objPath)) {
                throw IllegalStateException(
                    "Object ${contentOid.toHex()} appears delta-encoded but metadata is missing.",
                )
            }
            storeMetadata(repo, contentOid, BlobMetadata(isStoredAsDelta = false, chainDepth = 0))
            return contentOid to false
        }

        // Small or no base -> store raw.
        if (fileSize < MIN_DELTA_FILE_SIZE || recentBlobId == null) {
            val oid = FsObjectStore.writeBlob(repo, path)
            storeMetadata(repo, oid, BlobMetadata(isStoredAsDelta = false, chainDepth = 0))
            return oid to false
        }

        val baseMeta = getMetadata(repo, recentBlobId)
        if (baseMeta != null && baseMeta.chainDepth >= MAX_CHAIN_DEPTH) {
            val oid = FsObjectStore.writeBlob(repo, path)
            storeMetadata(repo, oid, BlobMetadata(isStoredAsDelta = false, chainDepth = 0))
            return oid to false
        }

        val baseTemp = materializeBlobToTempFile(repo, recentBlobId)
        val tempDelta = Files.createTempFile("timetree-delta", ".tmp")
        val reconstructedTemp = Files.createTempFile("timetree-verify", ".tmp")

        try {
            val signature =
                Files.newInputStream(baseTemp).use { baseInput ->
                    deltaAlgo.makeSignature(baseInput, DEFAULT_BLOCK_SIZE)
                }
            val delta =
                Files.newInputStream(path).use { targetInput ->
                    deltaAlgo.makeDelta(targetInput, signature)
                }
            Files.newOutputStream(tempDelta).use { out -> DeltaIO.write(delta, out) }

            val deltaSize = Files.size(tempDelta)
            if (deltaSize > (fileSize * MAX_DELTA_RATIO).toLong()) {
                Files.deleteIfExists(tempDelta)
                val oid = FsObjectStore.writeBlob(repo, path)
                storeMetadata(repo, oid, BlobMetadata(isStoredAsDelta = false, chainDepth = 0))
                return oid to false
            }

            val parsedDelta = Files.newInputStream(tempDelta).use { DeltaIO.read(it) }
            Files.newOutputStream(reconstructedTemp).use { verifyOut ->
                deltaAlgo.applyDelta(baseTemp, parsedDelta, verifyOut)
            }
            val reconstructedOid = FsObjectStore.computeBlobHash(reconstructedTemp)
            if (reconstructedOid != contentOid) {
                Files.deleteIfExists(tempDelta)
                val oid = FsObjectStore.writeBlob(repo, path)
                storeMetadata(repo, oid, BlobMetadata(isStoredAsDelta = false, chainDepth = 0))
                return oid to false
            }

            writeDeltaBlobAtomic(repo, contentOid, tempDelta)
            storeMetadata(
                repo,
                contentOid,
                BlobMetadata(
                    isStoredAsDelta = true,
                    chainDepth = (baseMeta?.chainDepth ?: 0) + 1,
                    baseOid = recentBlobId,
                ),
            )
            return contentOid to true
        } finally {
            Files.deleteIfExists(baseTemp)
            Files.deleteIfExists(tempDelta)
            Files.deleteIfExists(reconstructedTemp)
        }
    }

    fun streamBlobContent(
        repo: RepoLayout,
        oid: ObjectId,
        out: OutputStream,
        currentDepth: Int = 0,
    ) {
        if (currentDepth > MAX_CHAIN_DEPTH) {
            throw IllegalStateException("Delta chain depth exceeds maximum ($MAX_CHAIN_DEPTH).")
        }

        val meta = getMetadata(repo, oid)
        val objPath = objectPath(repo, oid)
        if (!Files.exists(objPath)) throw IllegalStateException("Object ${oid.toHex()} not found")

        if (meta == null) {
            if (looksLikeDeltaObject(objPath)) {
                throw IllegalStateException("Object ${oid.toHex()} looks delta-encoded but metadata is missing.")
            }
            Files.newInputStream(objPath).use { it.copyTo(out) }
            return
        }

        if (!meta.isStoredAsDelta) {
            Files.newInputStream(objPath).use { it.copyTo(out) }
            return
        }

        val baseOid = meta.baseOid ?: throw IllegalStateException("Delta blob ${oid.toHex()} missing baseOid")
        val baseTemp = Files.createTempFile("timetree-base", ".tmp")
        try {
            Files.newOutputStream(baseTemp).use { baseOut ->
                streamBlobContent(repo, baseOid, baseOut, currentDepth + 1)
            }
            val delta = Files.newInputStream(objPath).use { DeltaIO.read(it) }
            deltaAlgo.applyDelta(baseTemp, delta, out)
        } finally {
            Files.deleteIfExists(baseTemp)
        }
    }

    private fun storeMetadata(
        repo: RepoLayout,
        oid: ObjectId,
        meta: BlobMetadata,
    ) {
        val metaFile = metadataPath(repo, oid)
        if (!Files.isDirectory(metaFile.parent)) Files.createDirectories(metaFile.parent)
        val content =
            buildString {
                appendLine("version=1")
                appendLine("is_delta=${meta.isStoredAsDelta}")
                appendLine("chain_depth=${meta.chainDepth}")
                if (meta.baseOid != null) appendLine("base_oid=${meta.baseOid.toHex()}")
            }
        AtomicFile(metaFile).writeUtf8(content)
    }

    private fun getMetadata(
        repo: RepoLayout,
        oid: ObjectId,
    ): BlobMetadata? {
        val metaFile = metadataPath(repo, oid)
        if (!Files.exists(metaFile)) return null

        var isStoredAsDelta = false
        var chainDepth = 0
        var baseOid: ObjectId? = null

        Files.readAllLines(metaFile).forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size != 2) return@forEach
            when (parts[0]) {
                "is_delta" -> isStoredAsDelta = parts[1].toBoolean()
                "chain_depth" -> chainDepth = parts[1].toInt()
                "base_oid" -> baseOid = ObjectId.fromHex(parts[1])
            }
        }

        return BlobMetadata(isStoredAsDelta, chainDepth, baseOid)
    }

    private fun writeDeltaBlobAtomic(
        repo: RepoLayout,
        contentOid: ObjectId,
        deltaFile: Path,
    ) {
        val objPath = objectPath(repo, contentOid)
        if (!Files.isDirectory(objPath.parent)) Files.createDirectories(objPath.parent)
        if (!Files.exists(objPath)) {
            Files.copy(deltaFile, objPath, StandardCopyOption.COPY_ATTRIBUTES)
        }
    }

    private fun materializeBlobToTempFile(
        repo: RepoLayout,
        oid: ObjectId,
    ): Path {
        val temp = Files.createTempFile("timetree-blob", ".tmp")
        Files.newOutputStream(temp).use { out ->
            streamBlobContent(repo, oid, out, 0)
        }
        return temp
    }

    private fun objectPath(
        repo: RepoLayout,
        oid: ObjectId,
    ): Path {
        val (dir, leaf) = FsObjectStore.dirAndLeaf(repo, oid)
        return dir.resolve(leaf)
    }

    private fun metadataPath(
        repo: RepoLayout,
        oid: ObjectId,
    ): Path {
        val hex = oid.toHex()
        return repo.meta
            .resolve("blob-meta")
            .resolve(hex.take(2))
            .resolve(hex.substring(2))
    }

    private fun looksLikeDeltaObject(path: Path): Boolean {
        if (!Files.exists(path)) return false
        Files.newInputStream(path).use { input ->
            val buf = ByteArray(5)
            val n = input.read(buf)
            return n >= 5 && buf.contentEquals(TTDL_MAGIC)
        }
    }
}
