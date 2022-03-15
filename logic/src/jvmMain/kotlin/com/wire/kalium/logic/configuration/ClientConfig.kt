package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.client.DeviceType
import java.net.InetAddress

actual class ClientConfig {
    actual fun deviceType(): DeviceType {
        return DeviceType.Desktop
    }

    actual fun deviceModelName(): String {
        return System.getProperty("os.name")
    }

    actual fun deviceName(): String {
        return InetAddress.getLocalHost().hostName
    }

    actual fun clientType(): ClientType {
        return ClientType.Temporary
    }
}
