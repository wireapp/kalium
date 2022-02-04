package com.wire.kalium.logic.configuration

import com.wire.kalium.network.tools.BackendConfig
import com.wire.kalium.persistence.model.NetworkConfig
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerConfigMapperTest {

    private lateinit var serverConfigMapper: ServerConfigMapper

    @BeforeTest
    fun setup() {
        serverConfigMapper = ServerConfigMapperImpl()
    }

    @Test
    fun givenABackendConfig_whenMappingFromBackendConfig_thenValuesAreMappedCorrectly() {
        val backendConfig: BackendConfig = randomBackendConfig()
        val acuteValue: ServerConfig =
            with(backendConfig) { ServerConfig(apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl) }

        val expectedValue: ServerConfig = serverConfigMapper.fromBackendConfig(backendConfig)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenAServerConfig_whenMappingToBackendConfig_thenValuesAreMappedCorrectly() {
        val serverConfig: ServerConfig = randomServerConfig()
        val acuteValue: BackendConfig =
            with(serverConfig) { BackendConfig(apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl) }

        val expectedValue: BackendConfig = serverConfigMapper.toBackendConfig(serverConfig)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenANetworkConfig_whenMappingFromNetworkConfig_thenValuesAreMappedCorrectly() {
        val networkConfig: NetworkConfig = randomNetworkConfig()
        val acuteValue: ServerConfig =
            with(networkConfig) { ServerConfig(apiBaseUrl, accountBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl) }

        val expectedValue: ServerConfig = serverConfigMapper.fromNetworkConfig(networkConfig)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenAServerConfig_whenMappingToNetworkConfig_thenValuesAreMappedCorrectly() {
        val serverConfig: ServerConfig = randomServerConfig()
        val acuteValue: NetworkConfig =
            with(serverConfig) { NetworkConfig(apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl) }

        val expectedValue: NetworkConfig = serverConfigMapper.toNetworkConfig(serverConfig)
        assertEquals(expectedValue, acuteValue)
    }

    private companion object {
        val randomString get() = Random.nextBytes(64).decodeToString()
        fun randomBackendConfig(): BackendConfig =
            BackendConfig(randomString, randomString, randomString, randomString, randomString, randomString)

        fun randomServerConfig(): ServerConfig =
            ServerConfig(randomString, randomString, randomString, randomString, randomString, randomString)

        fun randomNetworkConfig(): NetworkConfig =
            NetworkConfig(randomString, randomString, randomString, randomString, randomString, randomString)
    }
}
