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

package com.wire.kalium.notificationinbox

import java.security.MessageDigest

internal actual fun sha256LowercaseHex(bytes: ByteArray): String =
    MessageDigest.getInstance(SHA_256).digest(bytes).toLowercaseHex()

private fun ByteArray.toLowercaseHex(): String = joinToString(separator = "") { byte ->
    byte.toUByte().toString(radix = HEX_RADIX).padStart(HEX_BYTE_WIDTH, '0')
}

private const val SHA_256 = "SHA-256"
private const val HEX_RADIX = 16
private const val HEX_BYTE_WIDTH = 2
