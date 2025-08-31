package dev.kamisama.core.hash

object Sha1Like {
    fun digest(bytes: ByteArray): String = "fake-hash-${bytes.size}"
}
