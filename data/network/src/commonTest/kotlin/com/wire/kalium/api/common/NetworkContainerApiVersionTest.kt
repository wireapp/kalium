/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.api.common

import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.network.SupportedApiVersions
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.api.v0.authenticated.networkContainer.AuthenticatedNetworkContainerV0
import com.wire.kalium.network.api.v0.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV0
import com.wire.kalium.network.api.v10.authenticated.networkContainer.AuthenticatedNetworkContainerV10
import com.wire.kalium.network.api.v10.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV10
import com.wire.kalium.network.api.v11.authenticated.networkContainer.AuthenticatedNetworkContainerV11
import com.wire.kalium.network.api.v11.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV11
import com.wire.kalium.network.api.v12.authenticated.networkContainer.AuthenticatedNetworkContainerV12
import com.wire.kalium.network.api.v12.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV12
import com.wire.kalium.network.api.v13.authenticated.networkContainer.AuthenticatedNetworkContainerV13
import com.wire.kalium.network.api.v13.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV13
import com.wire.kalium.network.api.v14.authenticated.networkContainer.AuthenticatedNetworkContainerV14
import com.wire.kalium.network.api.v14.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV14
import com.wire.kalium.network.api.v15.authenticated.networkContainer.AuthenticatedNetworkContainerV15
import com.wire.kalium.network.api.v15.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV15
import com.wire.kalium.network.api.v16.authenticated.networkContainer.AuthenticatedNetworkContainerV16
import com.wire.kalium.network.api.v16.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV16
import com.wire.kalium.network.api.v2.authenticated.networkContainer.AuthenticatedNetworkContainerV2
import com.wire.kalium.network.api.v2.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV2
import com.wire.kalium.network.api.v4.authenticated.networkContainer.AuthenticatedNetworkContainerV4
import com.wire.kalium.network.api.v4.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV4
import com.wire.kalium.network.api.v5.authenticated.networkContainer.AuthenticatedNetworkContainerV5
import com.wire.kalium.network.api.v5.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV5
import com.wire.kalium.network.api.v6.authenticated.networkContainer.AuthenticatedNetworkContainerV6
import com.wire.kalium.network.api.v6.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV6
import com.wire.kalium.network.api.v7.authenticated.networkContainer.AuthenticatedNetworkContainerV7
import com.wire.kalium.network.api.v7.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV7
import com.wire.kalium.network.api.v8.authenticated.networkContainer.AuthenticatedNetworkContainerV8
import com.wire.kalium.network.api.v8.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV8
import com.wire.kalium.network.api.v9.authenticated.networkContainer.AuthenticatedNetworkContainerV9
import com.wire.kalium.network.api.v9.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV9
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.fail

class NetworkContainerApiVersionTest {

    @Test
    fun givenSupportedApiVersions_whenCreatingAuthenticatedNetworkContainer_thenMatchingContainerIsReturned() {
        SupportedApiVersions.forEach { apiVersion ->
            val container = AuthenticatedNetworkContainer.create(
                sessionManager = TestSessionManager(serverConfig(apiVersion)),
                selfUserId = QualifiedID("self-user", "wire.com"),
                userAgent = "test/useragent",
                certificatePinning = emptyMap(),
                mockEngine = mockEngine(),
                mockWebSocketSession = null,
                kaliumLogger = kaliumLogger
            )

            assertAuthenticatedContainer(apiVersion, container)
        }
    }

    @Test
    fun givenSupportedApiVersions_whenCreatingUnauthenticatedNetworkContainer_thenMatchingContainerIsReturned() {
        SupportedApiVersions.forEach { apiVersion ->
            val container = UnauthenticatedNetworkContainer.create(
                serverConfigDTO = serverConfig(apiVersion),
                proxyCredentials = null,
                userAgent = "test/useragent",
                developmentApiEnabled = false,
                certificatePinning = emptyMap(),
                mockEngine = mockEngine()
            )

            assertUnauthenticatedContainer(apiVersion, container)
        }
    }

    private fun assertAuthenticatedContainer(apiVersion: Int, container: AuthenticatedNetworkContainer) {
        when (apiVersion) {
            0, 1 -> assertIs<AuthenticatedNetworkContainerV0>(container)
            2 -> assertIs<AuthenticatedNetworkContainerV2>(container)
            4 -> assertIs<AuthenticatedNetworkContainerV4>(container)
            5 -> assertIs<AuthenticatedNetworkContainerV5>(container)
            6 -> assertIs<AuthenticatedNetworkContainerV6>(container)
            7 -> assertIs<AuthenticatedNetworkContainerV7>(container)
            8 -> assertIs<AuthenticatedNetworkContainerV8>(container)
            9 -> assertIs<AuthenticatedNetworkContainerV9>(container)
            10 -> assertIs<AuthenticatedNetworkContainerV10>(container)
            11 -> assertIs<AuthenticatedNetworkContainerV11>(container)
            12 -> assertIs<AuthenticatedNetworkContainerV12>(container)
            13 -> assertIs<AuthenticatedNetworkContainerV13>(container)
            14 -> assertIs<AuthenticatedNetworkContainerV14>(container)
            15 -> assertIs<AuthenticatedNetworkContainerV15>(container)
            16 -> assertIs<AuthenticatedNetworkContainerV16>(container)
            else -> fail("No expected authenticated container for API version $apiVersion")
        }
    }

    private fun assertUnauthenticatedContainer(apiVersion: Int, container: UnauthenticatedNetworkContainer) {
        when (apiVersion) {
            0, 1 -> assertIs<UnauthenticatedNetworkContainerV0>(container)
            2 -> assertIs<UnauthenticatedNetworkContainerV2>(container)
            4 -> assertIs<UnauthenticatedNetworkContainerV4>(container)
            5 -> assertIs<UnauthenticatedNetworkContainerV5>(container)
            6 -> assertIs<UnauthenticatedNetworkContainerV6>(container)
            7 -> assertIs<UnauthenticatedNetworkContainerV7>(container)
            8 -> assertIs<UnauthenticatedNetworkContainerV8>(container)
            9 -> assertIs<UnauthenticatedNetworkContainerV9>(container)
            10 -> assertIs<UnauthenticatedNetworkContainerV10>(container)
            11 -> assertIs<UnauthenticatedNetworkContainerV11>(container)
            12 -> assertIs<UnauthenticatedNetworkContainerV12>(container)
            13 -> assertIs<UnauthenticatedNetworkContainerV13>(container)
            14 -> assertIs<UnauthenticatedNetworkContainerV14>(container)
            15 -> assertIs<UnauthenticatedNetworkContainerV15>(container)
            16 -> assertIs<UnauthenticatedNetworkContainerV16>(container)
            else -> fail("No expected unauthenticated container for API version $apiVersion")
        }
    }

    private fun serverConfig(apiVersion: Int) = TEST_BACKEND_CONFIG.copy(
        metaData = TEST_BACKEND_CONFIG.metaData.copy(
            commonApiVersion = ApiVersionDTO.Valid(apiVersion)
        )
    )

    private fun mockEngine() = MockEngine {
        error("No request expected")
    }

    private class TestSessionManager(private val serverConfig: ServerConfigDTO) : SessionManager {
        override suspend fun session(): SessionDTO? = null

        override fun serverConfig(): ServerConfigDTO = serverConfig

        override fun nomadServiceUrl(): String? = null

        override suspend fun updateToken(
            accessTokenApi: AccessTokenApi,
            oldRefreshToken: String?
        ): SessionDTO = error("No token refresh expected")

        override fun proxyCredentials(): ProxyCredentialsDTO? = null
    }
}
