package com.wire.kalium.logic.util

/**
 *
 * TODO: Move to a utils module?
 */
expect object Base64 {
    fun encodeToBase64(originalString: ByteArray): ByteArray
    fun decodeFromBase64(encoded: ByteArray): ByteArray
}
