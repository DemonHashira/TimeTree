package dev.kamisama.core.hash

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * Tests for SHA-1 hash implementation.
 */
class Sha1Test :
    StringSpec({
        "SHA-1 should match NIST test vector for 'abc'" {
            val id = HashAlgorithm.computeAll("abc".toByteArray())
            id.toHex() shouldBe "a9993e364706816aba3e25717850c26c9cd0d89d"
        }

        "SHA-1 should match empty string vector" {
            val id = HashAlgorithm.computeAll(ByteArray(0))
            id.toHex() shouldBe "da39a3ee5e6b4b0d3255bfef95601890afd80709"
        }

        "SHA-1 should be deterministic for arbitrary inputs" {
            checkAll(Arb.list(Arb.byte(), 0..1000)) { data ->
                val bytes = data.toByteArray()
                val hash1 = HashAlgorithm.computeAll(bytes)
                val hash2 = HashAlgorithm.computeAll(bytes)
                hash1 shouldBe hash2
            }
        }

        "SHA-1 should produce different hashes for different inputs" {
            checkAll(Arb.list(Arb.byte(), 1..100)) { data ->
                val bytes = data.toByteArray()
                val original = HashAlgorithm.computeAll(bytes)
                val modified = HashAlgorithm.computeAll(bytes + byteArrayOf(0xFF.toByte()))
                original shouldNotBe modified
            }
        }

        "SHA-1 should produce 20-byte (160-bit) hashes" {
            checkAll(Arb.list(Arb.byte(), 0..100)) { data ->
                val bytes = data.toByteArray()
                val hash = HashAlgorithm.computeAll(bytes)
                hash.toHex().length shouldBe 40
            }
        }

        "SHA-1 incremental API should produce same result as single update" {
            val message = "The quick brown fox jumps over the lazy dog".toByteArray()

            val hashAll = HashAlgorithm.computeAll(message)

            val hasher = Sha1()
            hasher.update(message, 0, 10)
            hasher.update(message, 10, 15)
            hasher.update(message, 25, message.size - 25)
            val hashIncremental = hasher.digest()

            hashAll shouldBe hashIncremental
        }

        "SHA-1 digest() should reset state for reuse" {
            val message1 = "first message".toByteArray()
            val message2 = "second message".toByteArray()

            val hasher = Sha1()

            hasher.update(message1)
            val hash1 = hasher.digest()

            hasher.update(message2)
            val hash2 = hasher.digest()

            hash1 shouldBe HashAlgorithm.computeAll(message1)
            hash2 shouldBe HashAlgorithm.computeAll(message2)
            hash1 shouldNotBe hash2
        }
    })
