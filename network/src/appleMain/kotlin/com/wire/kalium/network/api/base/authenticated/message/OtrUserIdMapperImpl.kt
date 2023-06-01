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

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSUUID

internal class OtrUserIdMapperImpl : OtrUserIdMapper {

    override fun toOtrUserId(userId: String): ByteArray {
        val uuid = NSUUID(userId)

        val nativeBytes = ByteArray(USER_UID_BYTE_COUNT)
        nativeBytes.usePinned {
            uuid.getUUIDBytes(it.addressOf(0).reinterpret())
        }
        return nativeBytes
    }

    companion object {
        private const val USER_UID_BYTE_COUNT = 16
    }
}

internal actual fun provideOtrUserIdMapper(): OtrUserIdMapper = OtrUserIdMapperImpl()
