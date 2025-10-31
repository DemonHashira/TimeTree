package dev.kamisama.core.delta

import dev.kamisama.core.hash.ObjectId
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

/**
 * Delta operation: either copy from a basis or insert new data.
 */
sealed class DeltaOp {
    data class Copy(
        val offset: Long,
        val length: Int,
    ) : DeltaOp()

    data class Insert(
        val data: ByteArray,
    ) : DeltaOp() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Insert) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }
}

/**
 * Delta describes how to reconstruct a target file from a basis file.
 */
data class Delta(
    val blockSize: Int,
    val ops: List<DeltaOp>,
)

/**
 * Signature of a single block in the basis file.
 */
data class BlockSignature(
    val index: Int,
    val weak: Int,
    val strong: ObjectId,
)

/**
 * Complete signature of a basis file for delta generation.
 */
data class Signature(
    val blockSize: Int,
    val blocks: List<BlockSignature>,
)

/**
 * Interface for the rsync-style delta algorithm.
 */
interface DeltaAlgorithm {
    /**
     * Generate a signature for a basis file.
     */
    fun makeSignature(
        basis: InputStream,
        blockSize: Int = 8192,
    ): Signature

    /**
     * Generate a delta by comparing a target against a signature.
     */
    fun makeDelta(
        target: InputStream,
        sig: Signature,
    ): Delta

    /**
     * Apply a delta to a basis file to reconstruct the target.
     */
    fun applyDelta(
        basisPath: Path,
        delta: Delta,
        out: OutputStream,
    )
}
