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

import java.nio.ByteBuffer
import java.util.UUID

internal class OtrUserIdMapperImpl : OtrUserIdMapper {

    override fun toOtrUserId(userId: String): ByteArray {
        val bytes = ByteArray(USER_UID_BYTE_COUNT)
        val byteBuffer = ByteBuffer.wrap(bytes).asLongBuffer()
        val uuid = UUID.fromString(userId)
        byteBuffer.put(uuid.mostSignificantBits)
        byteBuffer.put(uuid.leastSignificantBits)
        return bytes
    }

    companion object {
        private const val USER_UID_BYTE_COUNT = 16
    }
}

internal actual fun provideOtrUserIdMapper(): OtrUserIdMapper = OtrUserIdMapperImpl()
