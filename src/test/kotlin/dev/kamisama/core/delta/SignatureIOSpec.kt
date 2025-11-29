package dev.kamisama.core.delta

import dev.kamisama.core.delta.io.SignatureIO
import dev.kamisama.core.hash.ObjectId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Tests for signature serialization.
 */
class SignatureIOSpec :
    FunSpec({

        test("round trip empty signature") {
            val sig = Signature(8192, emptyList())
            val out = ByteArrayOutputStream()
            SignatureIO.write(sig, out)

            val read = SignatureIO.read(ByteArrayInputStream(out.toByteArray()))
            read.blockSize shouldBe sig.blockSize
            read.blocks.size shouldBe 0
        }

        test("round trip signature with blocks") {
            val blocks =
                listOf(
                    BlockSignature(0, 0x12345678, ObjectId.fromHex("a".repeat(40))),
                    BlockSignature(1, 0xABCDEF01.toInt(), ObjectId.fromHex("b".repeat(40))),
                    BlockSignature(2, 0x11223344, ObjectId.fromHex("c".repeat(40))),
                )
            val sig = Signature(4096, blocks)
            val out = ByteArrayOutputStream()
            SignatureIO.write(sig, out)

            val read = SignatureIO.read(ByteArrayInputStream(out.toByteArray()))
            read.blockSize shouldBe sig.blockSize
            read.blocks.size shouldBe 3

            for (i in blocks.indices) {
                read.blocks[i].index shouldBe blocks[i].index
                read.blocks[i].weak shouldBe blocks[i].weak
                read.blocks[i].strong shouldBe blocks[i].strong
            }
        }

        test("round trip arbitrary signatures") {
            val objectIdGen =
                Arb.list(Arb.int(0..15), 40..40).map { ints ->
                    val hexChars = ints.map { "0123456789abcdef"[it] }.joinToString("")
                    ObjectId.fromHex(hexChars)
                }

            val blockSigArb: Arb<BlockSignature> =
                Arb.bind(
                    Arb.int(0..10000),
                    Arb.int(),
                    objectIdGen,
                ) { idx: Int, weak: Int, strong: ObjectId -> BlockSignature(idx, weak, strong) }

            val signatureArb: Arb<Signature> =
                Arb.bind(
                    Arb.int(64..8192),
                    Arb.list(blockSigArb, 0..100),
                ) { blockSize: Int, blocks: List<BlockSignature> -> Signature(blockSize, blocks) }

            checkAll(signatureArb) { sig: Signature ->
                val out = ByteArrayOutputStream()
                SignatureIO.write(sig, out)

                val read = SignatureIO.read(ByteArrayInputStream(out.toByteArray()))
                read.blockSize shouldBe sig.blockSize
                read.blocks.size shouldBe sig.blocks.size

                for (i in read.blocks.indices) {
                    read.blocks[i].index shouldBe sig.blocks[i].index
                    read.blocks[i].weak shouldBe sig.blocks[i].weak
                    read.blocks[i].strong shouldBe sig.blocks[i].strong
                }
            }
        }

        test("invalid magic is rejected") {
            val badData = "BADMAGIC".toByteArray()
            shouldThrow<IllegalArgumentException> {
                SignatureIO.read(ByteArrayInputStream(badData))
            }
        }

        test("truncated signature is rejected") {
            val out = ByteArrayOutputStream()
            out.write(byteArrayOf('T'.code.toByte(), 'T'.code.toByte()))

            shouldThrow<Exception> {
                SignatureIO.read(ByteArrayInputStream(out.toByteArray()))
            }
        }
    })
