package dev.kamisama.core.delta

import dev.kamisama.core.hash.ObjectId
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

/**
 * Delta operation for reconstructing files.
 */
sealed class DeltaOp {
    /** Copy bytes from a basis file at offset. */
    data class Copy(
        val offset: Long,
        val length: Int,
    ) : DeltaOp()

    /** Insert new data bytes. */
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
 * Delta describes reconstruction from basis to target.
 */
data class Delta(
    val blockSize: Int,
    val ops: List<DeltaOp>,
)

/** Block signature with weak and strong checksums. */
data class BlockSignature(
    val index: Int,
    val weak: Int,
    val strong: ObjectId,
)

/** Complete signature of a basis file. */
data class Signature(
    val blockSize: Int,
    val blocks: List<BlockSignature>,
)

/** Interface for rsync-style delta algorithm. */
interface DeltaAlgorithm {
    fun makeSignature(
        basis: InputStream,
        blockSize: Int = 8192,
    ): Signature

    fun makeDelta(
        target: InputStream,
        sig: Signature,
    ): Delta

    fun applyDelta(
        basisPath: Path,
        delta: Delta,
        out: OutputStream,
    )
}
