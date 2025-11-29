package dev.kamisama.core.objects

import dev.kamisama.core.hash.HashAlgorithm
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe

/**
 * Tests for namespace separation from Git.
 */
class DomainSeparationTest :
    StringSpec({
        "namespace changes the id compared to plain git-style header" {
            val content = "hello".toByteArray()

            val gitHeader = "blob ${content.size}\u0000".toByteArray()
            val ttHeader = ObjectHeaders.blobHeader(content.size.toLong())

            val gitId = HashAlgorithm.computeAll(gitHeader + content).toHex()
            val ttId = HashAlgorithm.computeAll(ttHeader + content).toHex()

            ttId shouldNotBe gitId
        }
    })
