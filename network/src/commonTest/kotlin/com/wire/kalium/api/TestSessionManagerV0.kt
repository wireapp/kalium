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

package com.wire.kalium.api

import com.wire.kalium.api.json.model.testCredentials
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.base.model.SessionDTO
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.session.SessionManager

class TestSessionManagerV0 : SessionManager {
    private val serverConfig = TEST_BACKEND_CONFIG
    private var session = testCredentials

    override suspend fun session(): SessionDTO = session
    override fun serverConfig(): ServerConfigDTO = serverConfig
    override suspend fun updateToken(
        accessTokenApi: AccessTokenApi,
        oldAccessToken: String,
        oldRefreshToken: String
    ): SessionDTO {
        TODO("Not yet implemented")
    }

    override fun proxyCredentials(): ProxyCredentialsDTO? =
        ProxyCredentialsDTO("username", "password")

    companion object {
        val SESSION = testCredentials
    }

}
