package com.wire.kalium.logic.util

internal actual class SecureRandom actual constructor() {

    private val random get() = java.security.SecureRandom.getInstanceStrong()

    actual fun nextBytes(length: Int): ByteArray = ByteArray(length).apply {
        random.nextBytes(this)
    }
    actual fun nextInt(bound: Int): Int = random.nextInt(bound)
}
