package com.wire.kalium.network

import com.wire.kalium.network.tools.ServerConfigDTO

interface AuthServerConfigManager {
    fun getCurrentAuthServer(): ServerConfigDTO
}
