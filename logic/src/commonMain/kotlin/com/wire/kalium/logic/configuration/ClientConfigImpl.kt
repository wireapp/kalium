package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.client.DeviceType

interface ClientConfig {
    fun deviceType(): DeviceType
    fun deviceModelName(): String
    fun deviceName(): String
    fun clientType(): ClientType
}

expect class ClientConfigImpl : ClientConfig
