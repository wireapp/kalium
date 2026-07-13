/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wire.kalium.notificationinbox

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

internal actual fun sha256LowercaseHex(bytes: ByteArray): String {
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    val pointer = digest.usePinned { digestPinned ->
        if (bytes.isEmpty()) {
            CC_SHA256(null, 0u, digestPinned.addressOf(0).reinterpret<UByteVar>())
        } else {
            bytes.usePinned { bytesPinned ->
                CC_SHA256(
                    bytesPinned.addressOf(0),
                    bytes.size.toUInt(),
                    digestPinned.addressOf(0).reinterpret<UByteVar>()
                )
            }
        }
    }
    check(pointer != null) { "CommonCrypto SHA-256 failed" }
    return digest.joinToString(separator = "") { byte ->
        byte.toUByte().toString(radix = HEX_RADIX).padStart(HEX_BYTE_WIDTH, '0')
    }
}

private const val HEX_RADIX = 16
private const val HEX_BYTE_WIDTH = 2
