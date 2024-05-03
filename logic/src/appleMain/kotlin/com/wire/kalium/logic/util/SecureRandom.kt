/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.util

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.posix.arc4random_uniform

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
