package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.configuration.ServerConfigMapper
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.tools.BackendConfig
import com.wire.kalium.persistence.model.NetworkConfig
import com.wire.kalium.persistence.model.PersistenceSession
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionMapperTest {

    @Mock
    val serverConfigMapper: ServerConfigMapper = mock(classOf<ServerConfigMapper>())

    private lateinit var sessionMapper: SessionMapper

    @BeforeTest
    fun setup() {
        sessionMapper = SessionMapperImpl(serverConfigMapper)
    }


    @Test
    fun givenAnAuthSession_whenMappingToSessionCredentials_thenValuesAreMappedCorrectly() {
        val authSession: AuthSession = randomAuthSession()

        val acuteValue: SessionDTO =
            with(authSession) { SessionDTO(userId ,tokenType, accessToken, refreshToken) }

        val expectedValue: SessionDTO = sessionMapper.toSessionDTO(authSession)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenAnAuthSession_whenMappingToPersistenceSession_thenValuesAreMappedCorrectly() {
        val authSession: AuthSession = randomAuthSession()
        val networkConfig = with(authSession.serverConfig) {
            NetworkConfig(apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, title)
        }

        given(serverConfigMapper).invocation { toNetworkConfig(authSession.serverConfig) }.then { networkConfig }

        val acuteValue: PersistenceSession =
            with(authSession) {
                PersistenceSession(
                    userId = userId,
                    tokenType = tokenType,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    networkConfig = networkConfig
                )
            }

        val expectedValue: PersistenceSession = sessionMapper.toPersistenceSession(authSession)
        assertEquals(expectedValue, acuteValue)
        verify(serverConfigMapper).invocation { toNetworkConfig(authSession.serverConfig) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAPersistenceSession_whenMappingFromPersistenceSession_thenValuesAreMappedCorrectly() {
        val persistenceSession: PersistenceSession = randomPersistenceSession()
        val serverConfig = with(persistenceSession.networkConfig) {
            ServerConfig(apiBaseUrl, accountBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, title)
        }

        given(serverConfigMapper).invocation { fromNetworkConfig(persistenceSession.networkConfig) }.then { serverConfig }

        val acuteValue: AuthSession =
            with(persistenceSession) {
                AuthSession(
                    userId = userId,
                    tokenType = tokenType,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    serverConfig = serverConfig
                )
            }

        val expectedValue: AuthSession = sessionMapper.fromPersistenceSession(persistenceSession)
        assertEquals(expectedValue, acuteValue)
        verify(serverConfigMapper).invocation { fromNetworkConfig(persistenceSession.networkConfig) }.wasInvoked(exactly = once)
    }


    private companion object {
        val randomString get() = Random.nextBytes(64).decodeToString()
        fun randomBackendConfig(): BackendConfig =
            BackendConfig(randomString, randomString, randomString, randomString, randomString, randomString, randomString)

        fun randomAuthSession(): AuthSession = AuthSession(randomString, randomString, randomString, randomString, randomServerConfig())
        fun randomPersistenceSession(): PersistenceSession = PersistenceSession(randomString, randomString, randomString, randomString, randomNetworkConfig())

        fun randomServerConfig(): ServerConfig =
            ServerConfig(randomString, randomString, randomString, randomString, randomString, randomString, randomString)

        fun randomNetworkConfig(): NetworkConfig =
            NetworkConfig(randomString, randomString, randomString, randomString, randomString, randomString, randomString)
    }


}
