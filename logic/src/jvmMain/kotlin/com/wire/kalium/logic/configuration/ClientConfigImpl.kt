package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.client.DeviceType
import java.net.InetAddress

actual class ClientConfigImpl : ClientConfig {
    override fun deviceType(): DeviceType {
        return DeviceType.Desktop
    }

    override fun deviceModelName(): String {
        return System.getProperty("os.name")
    }

    override fun deviceName(): String {
        return InetAddress.getLocalHost().hostName
    }

    override fun clientType(): ClientType {
        return ClientType.Temporary
    }
}
