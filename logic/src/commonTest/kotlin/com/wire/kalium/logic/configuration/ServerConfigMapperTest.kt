package com.wire.kalium.logic.configuration

import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.model.ServerConfigEntity
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
        val serverConfigDTO: ServerConfigDTO = randomBackendConfig()
        val acuteValue: ServerConfig =
            with(serverConfigDTO) { ServerConfig(apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, title) }

        val expectedValue: ServerConfig = serverConfigMapper.fromDTO(serverConfigDTO)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenAServerConfig_whenMappingToBackendConfig_thenValuesAreMappedCorrectly() {
        val serverConfig: ServerConfig = randomServerConfig()
        val acuteValue: ServerConfigDTO =
            with(serverConfig) { ServerConfigDTO(apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, title) }

        val expectedValue: ServerConfigDTO = serverConfigMapper.toDTO(serverConfig)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenANetworkConfigEntity_whenMappingFromNetworkConfig_thenValuesAreMappedCorrectly() {
        val serverConfigEntity: ServerConfigEntity = randomNetworkConfig()
        val acuteValue: ServerConfig =
            with(serverConfigEntity) { ServerConfig(apiBaseUrl, accountBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, title) }

        val expectedValue: ServerConfig = serverConfigMapper.fromEntity(serverConfigEntity)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenAServerConfig_whenMappingToNetworkConfig_thenValuesAreMappedCorrectly() {
        val serverConfig: ServerConfig = randomServerConfig()
        val acuteValue: ServerConfigEntity =
            with(serverConfig) { ServerConfigEntity(apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, title) }

        val expectedValue: ServerConfigEntity = serverConfigMapper.toEntity(serverConfig)
        assertEquals(expectedValue, acuteValue)
    }

    private companion object {
        val randomString get() = Random.nextBytes(64).decodeToString()
        fun randomBackendConfig(): ServerConfigDTO =
            ServerConfigDTO(randomString, randomString, randomString, randomString, randomString, randomString, randomString)

        fun randomServerConfig(): ServerConfig =
            ServerConfig(randomString, randomString, randomString, randomString, randomString, randomString, randomString)

        fun randomNetworkConfig(): ServerConfigEntity =
            ServerConfigEntity(randomString, randomString, randomString, randomString, randomString, randomString, randomString)
    }
}
