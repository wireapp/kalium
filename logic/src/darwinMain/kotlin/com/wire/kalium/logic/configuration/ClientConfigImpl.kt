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
        return DeviceType.Desktop
    }

    override fun deviceModelName(): String {
        // TODO map to human readable device model names
        return hardwareModel() ?: "N/A"
    }

    private fun hardwareModel(): String? =
        memScoped {
            val len = alloc<ULongVar>()
            sysctlbyname("hw.model", null, len.ptr, null, 0)
            val buf = ByteArray(len.value.toInt())

            val result = buf.usePinned {
                sysctlbyname("hw.model", it.addressOf(0), len.ptr, null, 0)
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
