package com.wire.kalium.logic.util

import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64

actual object Base64 {
    actual fun encodeToBase64(originalString: ByteArray): ByteArray = originalString.encodeBase64().toByteArray()
    actual fun decodeFromBase64(encoded: ByteArray): ByteArray = encoded.decodeToString().decodeBase64Bytes()
}
