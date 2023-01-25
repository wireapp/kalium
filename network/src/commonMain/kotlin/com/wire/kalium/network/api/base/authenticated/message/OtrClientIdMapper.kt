/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.network.api.base.authenticated.message

import com.wire.kalium.protobuf.otr.ClientId

internal class OtrClientIdMapper {
    fun toOtrClientId(clientId: String): ClientId = ClientId(clientId.decodeHexToLong())
}

private fun String.decodeHexToLong(): Long {

    @Suppress("MagicNumber")
    fun unsignedLong(mostSignificantBits: Long, leastSignificantBits: Long) =
        (mostSignificantBits shl 32) or leastSignificantBits

    val a = this.padStart(length = 16, '0').chunked(size = 8) {
        it.toString().toLongOrNull(radix = 16) ?: 0
    }

    return unsignedLong(a[0], a[1])
}
