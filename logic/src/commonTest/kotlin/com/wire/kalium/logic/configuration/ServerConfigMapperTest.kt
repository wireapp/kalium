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
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.persistence.model.ServerConfigEntity
import io.mockative.Mock
import io.mockative.every
import io.mockative.mock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServerConfigMapperTest {

    private lateinit var serverConfigMapper: ServerConfigMapper

    @Mock
    private val versionMapper = mock(ApiVersionMapper::class)

    @BeforeTest
    fun setup() {
        serverConfigMapper = ServerConfigMapperImpl(versionMapper)
        every{versionMapper.toDTO(SERVER_CONFIG_TEST.metaData.commonApiVersion) }.returns(ApiVersionDTO.Valid(1))
    }

    @Test
    fun givenAServerConfig_whenMappingToBackendConfig_thenValuesAreMappedCorrectly() {
        val serverConfig: ServerConfig = SERVER_CONFIG_TEST
        val expected: ServerConfigDTO =
            with(serverConfig) {
                ServerConfigDTO(
                    id = id,
                    ServerConfigDTO.Links(
                        links.api,
                        links.accounts,
                        links.webSocket,
                        links.blackList,
                        links.teams,
                        links.website,
                        links.title,
                        links.isOnPremises,
                        links.apiProxy?.let { ServerConfigDTO.ApiProxy(it.needsAuthentication, it.host, it.port) }
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
                        links.api,
                        links.accounts,
                        links.webSocket,
                        links.blackList,
                        links.teams,
                        links.website,
                        links.title,
                        links.isOnPremises,
                        links.apiProxy?.let { ServerConfig.ApiProxy(it.needsAuthentication, it.host, it.port) }
                    ),
                    ServerConfig.MetaData(
                        metaData.federation,
                        metaData.apiVersion.toCommonApiVersionType(),
                        metaData.domain
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
                    ServerConfigEntity.Links(
                        links.api,
                        links.accounts,
                        links.webSocket,
                        links.blackList,
                        links.teams,
                        links.website,
                        links.title,
                        links.isOnPremises,
                        links.apiProxy?.let {
                            ServerConfigEntity.ApiProxy(it.needsAuthentication, it.host, it.port)
                        }
                    ),
                    ServerConfigEntity.MetaData(
                        metaData.federation,
                        metaData.commonApiVersion.version,
                        metaData.domain
                    )
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
