package com.wire.kalium.logic.util

import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import io.ktor.utils.io.core.toByteArray

/**
 *
 * TODO(qol): Move to a utils module?
 */
object Base64 {
    fun encodeToBase64(originalString: ByteArray): ByteArray = originalString.encodeBase64().toByteArray()
    fun decodeFromBase64(encoded: ByteArray): ByteArray = encoded.decodeToString().decodeBase64Bytes()
}
