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

package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.client.DeviceType
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSProcessInfo
import platform.darwin.sysctlbyname

actual class ClientConfigImpl : ClientConfig {
    override fun deviceType(): DeviceType {
        // TODO: Figure out the actual darwin device type
        return DeviceType.Desktop
    }

    override fun deviceModelName(): String {
        // TODO map to human readable device model names
        return hardwareModel() ?: "N/A"
    }

    private fun hardwareModel(): String? =
        memScoped {
            val len = alloc<ULongVar>()
            sysctlbyname("hw.model", null, len.ptr, null, 0U)
            val buf = ByteArray(len.value.toInt())

            val result = buf.usePinned {
                sysctlbyname("hw.model", it.addressOf(0), len.ptr, null, 0U)
            }

            if (result == 0 && len.value > 0UL) {
                buf.decodeToString(0, len.value.toInt() - 1)
            } else {
                return null
            }
        }

    override fun deviceName(): String {
        return NSProcessInfo.processInfo.hostName
    }

    override fun clientType(): ClientType {
        return ClientType.Temporary
    }
}
