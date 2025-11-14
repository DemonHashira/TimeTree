package dev.kamisama.core.hash

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** Tests for SHA-1 hash implementation. */
class Sha1Test :
    StringSpec({
        "SHA-1 should match NIST test vector for 'abc'" {
            val id = Sha1Like.computeAll("abc".toByteArray())
            id.toHex() shouldBe "a9993e364706816aba3e25717850c26c9cd0d89d"
        }

        "SHA-1 should match empty string vector" {
            val id = Sha1Like.computeAll(ByteArray(0))
            id.toHex() shouldBe "da39a3ee5e6b4b0d3255bfef95601890afd80709"
        }
    })
