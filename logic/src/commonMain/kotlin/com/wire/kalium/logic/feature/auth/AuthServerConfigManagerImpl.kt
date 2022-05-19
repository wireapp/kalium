package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.network.AuthServerConfigManager
import com.wire.kalium.network.tools.ServerConfigDTO

internal class AuthServerConfigManagerImpl internal constructor(
    private val serverConfigRepository: ServerConfigRepository
) : AuthServerConfigManager {
    override fun getCurrentAuthServer(): ServerConfigDTO {
        TODO("Not yet implemented")
    }
}
