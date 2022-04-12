package com.wire.kalium.logic.configuration

import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.model.ServerConfigEntity
import io.ktor.http.Url
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
        val serverConfigDTO: ServerConfigDTO = serverConfigDTO()
        val acuteValue: ServerConfig =
            with(serverConfigDTO) {
                ServerConfig(
                    TODO(),
                    apiBaseUrl.toString(),
                    accountsBaseUrl.toString(),
                    webSocketBaseUrl.toString(),
                    blackListUrl.toString(),
                    teamsUrl.toString(),
                    websiteUrl.toString(),
                    title
                )
            }

        val expectedValue: ServerConfig = serverConfigMapper.fromDTO(serverConfigDTO)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenAServerConfig_whenMappingToBackendConfig_thenValuesAreMappedCorrectly() {
        val serverConfig: ServerConfig = serverConfig()
        val acuteValue: ServerConfigDTO =
            with(serverConfig) {
                ServerConfigDTO(
                    Url(apiBaseUrl),
                    Url(accountsBaseUrl),
                    Url(webSocketBaseUrl),
                    Url(blackListUrl),
                    Url(teamsUrl),
                    Url(websiteUrl),
                    title
                )
            }

        val expectedValue: ServerConfigDTO = serverConfigMapper.toDTO(serverConfig)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenANetworkConfigEntity_whenMappingFromNetworkConfig_thenValuesAreMappedCorrectly() {
        val serverConfigEntity: ServerConfigEntity = serverConfigEntity()
        val acuteValue: ServerConfig =
            with(serverConfigEntity) {
                ServerConfig(
                    id,
                    apiBaseUrl,
                    accountBaseUrl,
                    webSocketBaseUrl,
                    blackListUrl,
                    teamsUrl,
                    websiteUrl,
                    title
                )
            }

        val expectedValue: ServerConfig = serverConfigMapper.fromEntity(serverConfigEntity)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenAServerConfig_whenMappingToNetworkConfig_thenValuesAreMappedCorrectly() {
        val serverConfig: ServerConfig = serverConfig()
        val acuteValue: ServerConfigEntity =
            with(serverConfig) {
                ServerConfigEntity(
                    id,
                    apiBaseUrl,
                    accountsBaseUrl,
                    webSocketBaseUrl,
                    blackListUrl,
                    teamsUrl,
                    websiteUrl,
                    title
                )
            }

        val expectedValue: ServerConfigEntity = serverConfigMapper.toEntity(serverConfig)
        assertEquals(expectedValue, acuteValue)
    }

    private companion object {
        fun serverConfigDTO(): ServerConfigDTO =
            ServerConfigDTO(
                Url("https://test.api.com"), Url("https://test.account.com"), Url("https://test.ws.com"),
                Url("https://test.blacklist"), Url("https://test.teams.com"), Url("https://test.wire.com"), "Test Title"
            )

        fun serverConfig(): ServerConfig =
            ServerConfig(
                "config-id", "https://test.api.com", "https://test.account.com", "https://test.ws.com",
                "https://test.blacklist", "https://test.teams.com", "https://test.wire.com", "Test Title"
            )

        fun serverConfigEntity(): ServerConfigEntity =
            ServerConfigEntity(
                "config-id", "https://test.api.com", "https://test.account.com", "https://test.ws.com",
                "https://test.blacklist", "https://test.teams.com", "https://test.wire.com", "Test Title"
            )
    }
}
