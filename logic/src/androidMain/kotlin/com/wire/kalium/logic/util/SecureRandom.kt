package com.wire.kalium.logic.util

import android.os.Build

internal actual class SecureRandom actual constructor() {

    private val random
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            java.security.SecureRandom.getInstanceStrong()
        } else {
            java.security.SecureRandom()
        }

    actual fun nextBytes(length: Int): ByteArray = ByteArray(length).apply {
        random.nextBytes(this)
    }

    actual fun nextInt(bound: Int): Int = random.nextInt(bound)

}
