package dev.kamisama.core.delta

import dev.kamisama.core.delta.io.VarInt
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Tests for variable-length integer encoding.
 */
class VarIntSpec :
    DescribeSpec({

        describe("VarInt encoding") {

            it("round trip for arbitrary non-negative longs") {
                checkAll(Arb.long(0L..Long.MAX_VALUE)) { value ->
                    val out = ByteArrayOutputStream()
                    VarInt.write(out, value)
                    val read = VarInt.read(ByteArrayInputStream(out.toByteArray()))
                    read shouldBe value
                }
            }

            it("small values are compact") {
                val out = ByteArrayOutputStream()
                VarInt.write(out, 100L)
                out.size() shouldBe 1
            }

            it("values 128-16383 use 2 bytes") {
                val out = ByteArrayOutputStream()
                VarInt.write(out, 128L)
                out.size() shouldBe 2

                out.reset()
                VarInt.write(out, 16383L)
                out.size() shouldBe 2
            }

            it("EOF during read throws") {
                val incomplete = byteArrayOf(0x80.toByte())
                shouldThrow<Exception> {
                    VarInt.read(ByteArrayInputStream(incomplete))
                }
            }

            it("excessively long varint throws") {
                val tooLong = ByteArray(11) { 0x80.toByte() }
                shouldThrow<IllegalArgumentException> {
                    VarInt.read(ByteArrayInputStream(tooLong))
                }
            }
        }
    })
