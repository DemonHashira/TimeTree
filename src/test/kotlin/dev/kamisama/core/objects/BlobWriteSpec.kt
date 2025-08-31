package dev.kamisama.core.objects

import io.kotest.core.spec.style.StringSpec

class BlobWriteSpec :
    StringSpec({
        "placeholder blob write test" {
            FsObjectStore.storeDummy("hello world")
        }
    })
