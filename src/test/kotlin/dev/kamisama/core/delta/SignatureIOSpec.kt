package dev.kamisama.core.delta

import dev.kamisama.core.hash.ObjectId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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

        test("signature with many blocks serializes correctly") {
            val blocks =
                (0 until 10000).map {
                    BlockSignature(it, it * 12345, ObjectId.fromHex("a".repeat(40)))
                }
            val sig = Signature(8192, blocks)
            val out = ByteArrayOutputStream()
            SignatureIO.write(sig, out)

            val read = SignatureIO.read(ByteArrayInputStream(out.toByteArray()))
            read.blocks.size shouldBe 10000
            read.blockSize shouldBe sig.blockSize
        }
    })
