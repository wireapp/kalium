package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.configuration.server.ServerConfigMapperImpl
import com.wire.kalium.logic.configuration.server.toCommonApiVersionType
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfigDTO
import com.wire.kalium.logic.util.stubs.newServerConfigEntity
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.model.ServerConfigEntity
import io.ktor.http.Url
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServerConfigMapperTest {

    private lateinit var serverConfigMapper: ServerConfigMapper

    @BeforeTest
    fun setup() {
        serverConfigMapper = ServerConfigMapperImpl()
    }

    @Test
    fun givenAServerConfig_whenMappingToBackendConfig_thenValuesAreMappedCorrectly() {
        val serverConfig: ServerConfig = SERVER_CONFIG_TEST
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
        val serverConfigEntity: ServerConfigEntity = ENTITY_TEST
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
                    title,
                    federation,
                    commonApiVersion.toCommonApiVersionType(),
                    domain
                )
            }

        val expectedValue: ServerConfig = serverConfigMapper.fromEntity(serverConfigEntity)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenAServerConfig_whenMappingToNetworkConfig_thenValuesAreMappedCorrectly() {
        val serverConfig: ServerConfig = SERVER_CONFIG_TEST
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
                    title,
                    federation,
                    commonApiVersion.version,
                    domain
                )
            }

        val expectedValue: ServerConfigEntity = serverConfigMapper.toEntity(serverConfig)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenACommonApiVersion_whenMapping_thenValuesAreMappedCorrectly() {
        val expectedUnknown = -2
        val actualUnknown = expectedUnknown.toCommonApiVersionType()
        assertIs<CommonApiVersionType.Unknown>(actualUnknown)
        assertEquals(expectedUnknown, actualUnknown.version)

        val expectedNew = -1
        val actualNew = expectedNew.toCommonApiVersionType()
        assertIs<CommonApiVersionType.New>(actualNew)
        assertEquals(expectedNew, actualNew.version)

        val expectedValid = 1
        val actualValid = expectedValid.toCommonApiVersionType()
        assertIs<CommonApiVersionType.Valid>(actualValid)
        assertEquals(expectedValid, actualValid.version)
    }

    private companion object {
        val DTO_TEST: ServerConfigDTO = newServerConfigDTO(1)
        val SERVER_CONFIG_TEST: ServerConfig = newServerConfig(1)
        val ENTITY_TEST: ServerConfigEntity = newServerConfigEntity(1)
    }
}
