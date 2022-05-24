package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.configuration.server.ApiVersionMapper
import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.configuration.server.ServerConfigMapperImpl
import com.wire.kalium.logic.configuration.server.toCommonApiVersionType
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfigDTO
import com.wire.kalium.logic.util.stubs.newServerConfigEntity
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.model.ServerConfigEntity
import io.ktor.http.Url
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServerConfigMapperTest {

    private lateinit var serverConfigMapper: ServerConfigMapper

    @Mock
    private val versionMapper = mock(classOf<ApiVersionMapper>())

    @BeforeTest
    fun setup() {
        serverConfigMapper = ServerConfigMapperImpl(versionMapper)
        given(versionMapper).invocation { toDTO(SERVER_CONFIG_TEST.metaData.commonApiVersion) }.then { ApiVersionDTO.Valid(1) }
    }

    @Test
    fun givenAServerConfig_whenMappingToBackendConfig_thenValuesAreMappedCorrectly() {
        val serverConfig: ServerConfig = SERVER_CONFIG_TEST
        val expected: ServerConfigDTO =
            with(serverConfig) {
                ServerConfigDTO(
                    id = id,
                    ServerConfigDTO.Links(
                        Url(links.api),
                        Url(links.accounts),
                        Url(links.webSocket),
                        Url(links.blackList),
                        Url(links.teams),
                        Url(links.website),
                        links.title,
                    ),
                    ServerConfigDTO.MetaData(
                        metaData.federation,
                        ApiVersionDTO.Valid(1),
                        metaData.domain
                    )
                )
            }

        val actual: ServerConfigDTO = serverConfigMapper.toDTO(serverConfig)
        assertEquals(expected, actual)
    }

    @Test
    fun givenANetworkConfigEntity_whenMappingFromNetworkConfig_thenValuesAreMappedCorrectly() {
        val serverConfigEntity: ServerConfigEntity = ENTITY_TEST
        val acuteValue: ServerConfig =
            with(serverConfigEntity) {
                ServerConfig(
                    id,
                    ServerConfig.Links(
                        apiBaseUrl,
                        accountBaseUrl,
                        webSocketBaseUrl,
                        blackListUrl,
                        teamsUrl,
                        websiteUrl,
                        title,
                        ),
                    ServerConfig.MetaData(
                        federation,
                        commonApiVersion.toCommonApiVersionType(),
                        domain
                    )
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
                    links.api,
                    links.accounts,
                    links.webSocket,
                    links.blackList,
                    links.teams,
                    links.website,
                    links.title,
                    metaData.federation,
                    metaData.commonApiVersion.version,
                    metaData.domain
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
