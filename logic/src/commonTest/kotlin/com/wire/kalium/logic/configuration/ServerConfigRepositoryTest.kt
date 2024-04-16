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

import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigDataSource
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfigDTO
import com.wire.kalium.logic.util.stubs.newServerConfigEntity
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApi
import com.wire.kalium.network.api.base.unbound.versioning.VersionApi
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.model.ServerConfigEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class ServerConfigRepositoryTest {
    @Test
    fun givenValidCompatibleApiVersion_whenStoringConfigLocally_thenConfigIsStored() = runTest {
        val expected = newServerConfig(1)
        val expectedDTO = newServerConfigDTO(1)

        val expectedEntity = newServerConfigEntity(1)
        val (arrangement, repository) = Arrangement()
            .withApiAversionResponse(expectedDTO.metaData)
            .withConfigById(expectedEntity)
            .withConfigByLinks(null)
            .arrange()

        repository.fetchApiVersionAndStore(expected.links).shouldSucceed {
            assertEquals(expected, it)
        }

        coVerify {
            arrangement.versionApi.fetchApiVersion(any())
        }.wasInvoked(exactly = once)

        verify {
            arrangement.serverConfigDAO.configById(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.serverConfigDAO.insert(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenInValidCompatibleApiVersion_whenStoringConfigLocally_thenErrorIsPropagated() = runTest {
        val expected = newServerConfig(1).copy(metaData = ServerConfig.MetaData(false, CommonApiVersionType.Unknown, "domain"))
        val expectedMetaDataDTO = ServerConfigDTO.MetaData(false, ApiVersionDTO.Invalid.Unknown, "domain")
        val expectedEntity = newServerConfigEntity(1).copy(metaData = ServerConfigEntity.MetaData(false, -2, "domain"))

        val (arrangement, repository) = Arrangement()
            .withApiAversionResponse(expectedMetaDataDTO)
            .withConfigByLinks(expectedEntity)
            .arrange()

        repository.fetchApiVersionAndStore(expected.links).shouldFail() {
            assertEquals(ServerConfigFailure.UnknownServerVersion, it)
        }

        coVerify {
            arrangement.versionApi.fetchApiVersion(any())
        }.wasInvoked(exactly = once)

        verify {
            arrangement.serverConfigDAO.configById(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.serverConfigDAO.configByLinks(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.serverConfigDAO.insert(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenStoredConfig_whenAddingTheSameOneWithNewApiVersionParams_thenStoredOneShouldBeUpdatedAndReturned() = runTest {
        val (arrangement, repository) = Arrangement()
            .withUpdatedServerConfig()
            .arrange()

        repository
            .storeConfig(arrangement.expectedServerConfig.links, arrangement.expectedServerConfig.metaData)
            .shouldSucceed { assertEquals(arrangement.expectedServerConfig, it) }

        coVerify {
            arrangement.serverConfigDAO.configByLinks(any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.serverConfigDAO.insert(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.serverConfigDAO.updateApiVersion(any(), any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.serverConfigDAO.setFederationToTrue(any())
        }.wasInvoked(exactly = once)
        verify {
            arrangement.serverConfigDAO.configById(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_whenAddingNewOne_thenNewOneShouldBeInsertedAndReturned() = runTest {
        val expected = newServerConfig(1)
        val (arrangement, repository) = Arrangement()
            .withConfigForNewRequest(newServerConfigEntity(1))
            .arrange()

        repository
            .storeConfig(expected.links, expected.metaData)
            .shouldSucceed { assertEquals(it, expected) }

        coVerify {
            arrangement.serverConfigDAO.configByLinks(any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.serverConfigDAO.insert(any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.serverConfigDAO.updateApiVersion(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.serverConfigDAO.setFederationToTrue(any())
        }.wasNotInvoked()
        verify {
            arrangement.serverConfigDAO.configById(any())
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        val SERVER_CONFIG_URL = "https://test.test/test.json"
        val SERVER_CONFIG_RESPONSE = newServerConfigDTO(1)
        val SERVER_CONFIG = newServerConfig(1)

        @Mock
        val serverConfigApi = mock(ServerConfigApi::class)

        @Mock
        val serverConfigDAO = mock(ServerConfigurationDAO::class)

        @Mock
        val versionApi = mock(VersionApi::class)

        private var serverConfigRepository: ServerConfigRepository =
            ServerConfigDataSource(serverConfigDAO, versionApi)

        val serverConfigEntity = newServerConfigEntity(1)
        val expectedServerConfig = newServerConfig(1).copy(
            metaData = ServerConfig.MetaData(
                commonApiVersion = CommonApiVersionType.Valid(5),
                federation = true,
                domain = serverConfigEntity.metaData.domain
            )
        )

        suspend fun withConfigForNewRequest(serverConfigEntity: ServerConfigEntity): Arrangement {
            coEvery { serverConfigDAO.configByLinks(serverConfigEntity.links) }
                .returns(null)
            every {
                serverConfigDAO.configById(any())
            }.returns(newServerConfigEntity(1))
            return this
        }

        suspend fun withSuccessConfigResponse(): Arrangement {
            coEvery {
                serverConfigApi.fetchServerConfig(SERVER_CONFIG_URL)
            }.returns(NetworkResponse.Success(SERVER_CONFIG_RESPONSE.links, mapOf(), 200))
            return this
        }

        suspend fun withDaoEntityResponse(): Arrangement {
            coEvery {
                serverConfigDAO.allConfig()
            }.returns(listOf(newServerConfigEntity(1), newServerConfigEntity(2), newServerConfigEntity(3)))
            return this
        }

        fun withConfigById(serverConfig: ServerConfigEntity): Arrangement {
            every {
                serverConfigDAO.configById(any())
            }.returns(serverConfig)
            return this
        }

        suspend fun withConfigByLinks(serverConfigEntity: ServerConfigEntity?): Arrangement {
            coEvery {
                serverConfigDAO.configByLinks(any())
            }.returns(serverConfigEntity)
            return this
        }

        suspend fun withDaoEntityFlowResponse(): Arrangement {
            coEvery {
                serverConfigDAO.allConfigFlow()
            }.returns(flowOf(listOf(newServerConfigEntity(1), newServerConfigEntity(2), newServerConfigEntity(3))))
            return this
        }

        suspend fun withApiAversionResponse(serverConfigDTO: ServerConfigDTO.MetaData): Arrangement {
            coEvery {
                versionApi.fetchApiVersion(any())
            }.returns(NetworkResponse.Success(serverConfigDTO, mapOf(), 200))

            return this
        }

        suspend fun withUpdatedServerConfig(): Arrangement {
            val newServerConfigEntity = serverConfigEntity.copy(
                metaData = serverConfigEntity.metaData.copy(
                    apiVersion = 5,
                    federation = true
                )
            )

            coEvery {
                serverConfigDAO.configByLinks(serverConfigEntity.links)
            }.returns(serverConfigEntity)
            every {
                serverConfigDAO.configById(any())
            }.returns(
                newServerConfigEntity
            )

            return this
        }

        fun arrange() = this to serverConfigRepository

    }
}
