package com.wire.kalium.logic.util

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.posix.arc4random
import platform.posix.arc4random_uniform
import platform.posix.u_int32_t

internal actual class SecureRandom actual constructor() {
    actual fun nextBytes(length: Int): ByteArray {
        var bytes = ByteArray(length)

        // TODO handle failure case
        memScoped {
            bytes.usePinned {
                SecRandomCopyBytes(
                    kSecRandomDefault,
                    length.toULong(),
                    it.addressOf(0)
                )
            }
        }

        return bytes
    }

    actual fun nextInt(bound: Int): Int {
        // TODO replace with SecRandomCopyBytes?
        return arc4random_uniform(bound.toUInt()).toInt()
    }
}
