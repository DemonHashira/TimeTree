package dev.kamisama.core.objects

import dev.kamisama.core.hash.Sha1Like
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe

/**
 * Tests domain separation - ensures TimeTree objects have different hashes than Git objects.
 */
class DomainSeparationTest :
    StringSpec({
        "namespace changes the id compared to plain git-style header" {
            val content = "hello".toByteArray()

            // Standard Git blob header format
            val gitHeader = "blob ${content.size}\u0000".toByteArray()
            // TimeTree namespaced header format
            val ttHeader = ObjectHeaders.blobHeader(content.size.toLong())

            // Compute hashes for both formats
            val gitId = Sha1Like.computeAll(gitHeader + content).toHex()
            val ttId = Sha1Like.computeAll(ttHeader + content).toHex()

            // Verify namespace prevents hash collisions with Git
            ttId shouldNotBe gitId
        }
    })
