package dev.kamisama.core.delta

import dev.kamisama.core.hash.Sha1
import dev.kamisama.core.hash.Sha1Like
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.file.Path

/**
 * rsync-style delta engine using rolling checksums and strong hashes.
 */
class RsyncDelta(
    private val strongHashFactory: () -> Sha1Like = { Sha1() },
) : DeltaAlgorithm {
    companion object {
        private const val MIN_BLOCK_SIZE = 64
        private const val MAX_BLOCK_SIZE = 1024 * 1024
        private const val STREAM_CHUNK_SIZE = 65536
    }

    override fun makeSignature(
        basis: InputStream,
        blockSize: Int,
    ): Signature {
        validateBlockSize(blockSize)
        val blocks = mutableListOf<BlockSignature>()
        val buffer = ByteArray(blockSize)
        var index = 0

        while (true) {
            val bytesRead = basis.read(buffer)
            if (bytesRead <= 0) break

            val roller = RsyncRoller(blockSize)
            roller.init(buffer, 0, bytesRead)
            val weak = roller.weak()

            val hash = strongHashFactory()
            hash.update(buffer, 0, bytesRead)
            val strong = hash.digest()

            blocks.add(BlockSignature(index, weak, strong))
            index++
        }

        return Signature(blockSize, blocks)
    }

    override fun makeDelta(
        target: InputStream,
        sig: Signature,
    ): Delta {
        validateBlockSize(sig.blockSize)

        if (sig.blocks.isEmpty()) {
            return makeDeltaForEmptySignature(target, sig.blockSize)
        }

        val weakMap = buildWeakMap(sig)
        val ops = mutableListOf<DeltaOp>()
        val roller = RsyncRoller(sig.blockSize)
        val window = RingBuffer(sig.blockSize)
        val pendingInsert = ByteAccumulator()

        val firstByte = target.read()
        if (firstByte < 0) {
            return Delta(sig.blockSize, emptyList())
        }

        window.add(firstByte.toByte())
        roller.init(byteArrayOf(firstByte.toByte()), 0, 1)

        while (window.size() < sig.blockSize) {
            val b = target.read()
            if (b < 0) break
            window.add(b.toByte())
            roller.roll(b.toByte())
        }

        while (true) {
            val weak = roller.weak()
            val match = findMatch(window, weak, weakMap)

            if (match != null) {
                flushPending(pendingInsert, ops)
                ops.add(DeltaOp.Copy(match.index.toLong() * sig.blockSize, window.size()))

                window.clear()
                val nextByte = target.read()
                if (nextByte < 0) break

                window.add(nextByte.toByte())
                roller.init(byteArrayOf(nextByte.toByte()), 0, 1)

                while (window.size() < sig.blockSize) {
                    val b = target.read()
                    if (b < 0) break
                    window.add(b.toByte())
                    roller.roll(b.toByte())
                }

                if (window.isEmpty()) break
            } else {
                pendingInsert.add(window.get(0))

                val nextByte = target.read()
                if (nextByte < 0) {
                    for (i in 1 until window.size()) {
                        pendingInsert.add(window.get(i))
                    }
                    break
                }

                window.add(nextByte.toByte())
                roller.roll(nextByte.toByte())
            }
        }

        flushPending(pendingInsert, ops)
        return Delta(sig.blockSize, coalesceOps(ops))
    }

    override fun applyDelta(
        basisPath: Path,
        delta: Delta,
        out: OutputStream,
    ) {
        validateBlockSize(delta.blockSize)

        RandomAccessFile(basisPath.toFile(), "r").use { raf ->
            val buffer = ByteArray(8192)

            for (op in delta.ops) {
                when (op) {
                    is DeltaOp.Insert -> {
                        out.write(op.data)
                    }

                    is DeltaOp.Copy -> {
                        raf.seek(op.offset)
                        var remaining = op.length
                        while (remaining > 0) {
                            val toRead = minOf(remaining, buffer.size)
                            val bytesRead = raf.read(buffer, 0, toRead)
                            if (bytesRead < 0) {
                                throw IllegalStateException("Unexpected EOF in basis file at offset ${op.offset}")
                            }
                            out.write(buffer, 0, bytesRead)
                            remaining -= bytesRead
                        }
                    }
                }
            }
        }
    }

    private fun makeDeltaForEmptySignature(
        target: InputStream,
        blockSize: Int,
    ): Delta {
        val ops = mutableListOf<DeltaOp>()
        val buffer = ByteArray(STREAM_CHUNK_SIZE)

        while (true) {
            val bytesRead = target.read(buffer)
            if (bytesRead <= 0) break

            val chunk = ByteArray(bytesRead)
            System.arraycopy(buffer, 0, chunk, 0, bytesRead)
            ops.add(DeltaOp.Insert(chunk))
        }

        return Delta(blockSize, ops)
    }

    private fun buildWeakMap(sig: Signature): Map<Int, List<BlockSignature>> {
        val map = mutableMapOf<Int, MutableList<BlockSignature>>()
        for (block in sig.blocks) {
            map.computeIfAbsent(block.weak) { mutableListOf() }.add(block)
        }
        return map
    }

    private fun findMatch(
        window: RingBuffer,
        weak: Int,
        weakMap: Map<Int, List<BlockSignature>>,
    ): BlockSignature? {
        val candidates = weakMap[weak] ?: return null

        val windowBytes = window.toByteArray()
        val hash = strongHashFactory()
        hash.update(windowBytes, 0, windowBytes.size)
        val strong = hash.digest()

        return candidates.firstOrNull { it.strong == strong }
    }

    private fun flushPending(
        accumulator: ByteAccumulator,
        ops: MutableList<DeltaOp>,
    ) {
        if (!accumulator.isEmpty()) {
            ops.add(DeltaOp.Insert(accumulator.toByteArray()))
            accumulator.clear()
        }
    }

    private fun coalesceOps(ops: List<DeltaOp>): List<DeltaOp> {
        if (ops.isEmpty()) return ops

        val result = mutableListOf<DeltaOp>()
        var i = 0

        while (i < ops.size) {
            val op = ops[i]
            if (op is DeltaOp.Copy) {
                var totalLength = op.length
                var lastOffset = op.offset + op.length
                var j = i + 1

                while (j < ops.size) {
                    val nextOp = ops[j]
                    if (nextOp is DeltaOp.Copy && nextOp.offset == lastOffset) {
                        totalLength += nextOp.length
                        lastOffset = nextOp.offset + nextOp.length
                        j++
                    } else {
                        break
                    }
                }

                result.add(DeltaOp.Copy(op.offset, totalLength))
                i = j
            } else {
                result.add(op)
                i++
            }
        }

        return result
    }

    private fun validateBlockSize(blockSize: Int) {
        require(blockSize in MIN_BLOCK_SIZE..MAX_BLOCK_SIZE) {
            "Block size must be between $MIN_BLOCK_SIZE and $MAX_BLOCK_SIZE (got $blockSize)"
        }
    }
}
