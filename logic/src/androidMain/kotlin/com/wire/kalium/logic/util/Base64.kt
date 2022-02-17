package com.wire.kalium.logic.util

import android.util.Base64

actual object Base64 {
    actual fun encodeToBase64(originalString: ByteArray): ByteArray = Base64.encode(originalString, Base64.NO_WRAP)
    actual fun decodeFromBase64(encoded: ByteArray): ByteArray = Base64.decode(encoded, Base64.NO_WRAP)
}
