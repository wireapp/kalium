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

import app.cash.turbine.test
import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.CustomServerConfigDataSource
import com.wire.kalium.logic.configuration.server.CustomServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfigDTO
import com.wire.kalium.logic.util.stubs.newServerConfigEntity
import com.wire.kalium.network.BackendMetaDataUtil
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApi
import com.wire.kalium.network.api.base.unbound.versioning.VersionApi
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.model.ServerConfigEntity
import io.ktor.http.Url
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.properties.Delegates
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomServerConfigRepositoryTest {

    @Test
    fun givenUrl_whenFetchingServerConfigSuccess_thenTheSuccessIsReturned() = runTest {
        val (arrangement, repository) = Arrangement().withSuccessConfigResponse().arrange()
        val expected = SERVER_CONFIG.links
        val serverConfigUrl = SERVER_CONFIG_URL

        val actual = repository.fetchRemoteConfig(serverConfigUrl)

        actual.shouldSucceed { assertEquals(expected, it) }
        coVerify {
            arrangement.serverConfigApi.fetchServerConfig(serverConfigUrl)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_whenAddingTheSameOneWithNewApiVersionParams_thenStoredOneShouldBeUpdatedAndReturned() = runTest {
        val (arrangement, repository) = Arrangement()
            .withUpdatedServerConfig()
            .arrange()

        repository
            .storeConfig(expectedServerConfig.links, expectedServerConfig.metaData)
            .shouldSucceed { assertEquals(expectedServerConfig, it) }

        coVerify {
            arrangement.serverConfigurationDAO.configByLinks(any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.serverConfigurationDAO.insert(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.serverConfigurationDAO.updateServerMetaData(any(), any(), any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.serverConfigurationDAO.setFederationToTrue(any())
        }.wasInvoked(exactly = once)
        verify {
            arrangement.serverConfigurationDAO.configById(any())
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
            arrangement.serverConfigurationDAO.configByLinks(any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.serverConfigurationDAO.insert(any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.serverConfigurationDAO.updateServerMetaData(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.serverConfigurationDAO.setFederationToTrue(any())
        }.wasNotInvoked()
        verify {
            arrangement.serverConfigurationDAO.configById(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfigLinksAndVersionInfoData_whenAddingNewOne_thenCommonApiShouldBeCalculatedAndConfigShouldBeStored() = runTest {
        val expectedServerConfig = newServerConfig(1)
        val expectedServerConfigDTO = newServerConfigDTO(1)
        val expectedVersionInfo = ServerConfig.VersionInfo(
            expectedServerConfig.metaData.federation,
            listOf(expectedServerConfig.metaData.commonApiVersion.version),
            expectedServerConfig.metaData.domain,
            null
        )
        val (arrangement, repository) = Arrangement()
            .withConfigForNewRequest(newServerConfigEntity(1))
            .withCalculateApiVersion(expectedServerConfigDTO.metaData)
            .arrange()

        repository
            .storeConfig(expectedServerConfig.links, expectedVersionInfo)
            .shouldSucceed { assertEquals(it, expectedServerConfig) }

        verify {
            arrangement.backendMetaDataUtil.calculateApiVersion(any(), any(), any(), any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.serverConfigurationDAO.configByLinks(any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.serverConfigurationDAO.insert(any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.serverConfigurationDAO.updateServerMetaData(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.serverConfigurationDAO.setFederationToTrue(any())
        }.wasNotInvoked()
        verify {
            arrangement.serverConfigurationDAO.configById(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenServerLinks_whenCalledObserve_thenTheSuccessEmitFlowWithSererConfig() = runTest {
        val (arrangement, repository) = Arrangement()
            .withVersionApiResponse()
            .withConfigByLinks(serverConfigEntity)
            .withConfigById(serverConfigEntity)
            .withGetServerConfigByLinksFlow()
            .arrange()
        val expected = SERVER_CONFIG.links
        val serverConfigUrl = SERVER_CONFIG_URL

        val actual = repository.observeServerConfigByLinks(expected)

        actual.test {
            awaitItem().shouldSucceed { assertEquals(SERVER_CONFIG, it) }

            cancelAndIgnoreRemainingEvents()
        }
        coVerify {
            arrangement.versionApi.fetchApiVersion(eq(Url(SERVER_CONFIG_RESPONSE.links.api)))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        val serverConfigApi = mock(ServerConfigApi::class)
        val versionApi = mock(VersionApi::class)

        var developmentApiEnabled by Delegates.notNull<Boolean>()

        val serverConfigurationDAO = mock(ServerConfigurationDAO::class)

        init {
            developmentApiEnabled = false
        }

        val backendMetaDataUtil = mock(BackendMetaDataUtil::class)

        private var customServerConfigRepository: CustomServerConfigRepository =
            CustomServerConfigDataSource(versionApi, serverConfigApi, developmentApiEnabled, serverConfigurationDAO, backendMetaDataUtil)

        suspend fun withConfigForNewRequest(serverConfigEntity: ServerConfigEntity): Arrangement {
            every {
                serverConfigurationDAO.configById(any())
            }.returns(serverConfigEntity)
            coEvery {
                serverConfigurationDAO.configByLinks(any())
            }.returns(null)
            return this
        }

        suspend fun withSuccessConfigResponse(): Arrangement {
            coEvery { serverConfigApi.fetchServerConfig(SERVER_CONFIG_URL) }
                .returns(NetworkResponse.Success(SERVER_CONFIG_RESPONSE.links, mapOf(), 200))
            return this
        }

        suspend fun withDaoEntityResponse(): Arrangement {
            coEvery { serverConfigurationDAO.allConfig() }
                .returns(listOf(newServerConfigEntity(1), newServerConfigEntity(2), newServerConfigEntity(3)))
            return this
        }

        fun withConfigById(serverConfig: ServerConfigEntity): Arrangement {
            every {
                serverConfigurationDAO.configById(any())
            }.returns(serverConfig)
            return this
        }

        suspend fun withConfigByLinks(serverConfigEntity: ServerConfigEntity?): Arrangement {
            coEvery {
                serverConfigurationDAO.configByLinks(any())
            }.returns(serverConfigEntity)
            return this
        }

        suspend fun withDaoEntityFlowResponse(): Arrangement {
            coEvery { serverConfigurationDAO.allConfigFlow() }
                .returns(flowOf(listOf(newServerConfigEntity(1), newServerConfigEntity(2), newServerConfigEntity(3))))
            return this
        }

        suspend fun withGetServerConfigByLinksFlow(): Arrangement {
            coEvery { serverConfigurationDAO.getServerConfigByLinksFlow(any()) }
                .returns(flowOf(newServerConfigEntity(1)))
            return this
        }

        suspend fun withUpdatedServerConfig(): Arrangement {
            val newServerConfigEntity = serverConfigEntity.copy(
                metaData = serverConfigEntity.metaData.copy(
                    apiVersion = 5,
                    federation = true
                )
            )

            coEvery { serverConfigurationDAO.configByLinks(serverConfigEntity.links) }
                .returns(serverConfigEntity)
            every {
                serverConfigurationDAO.configById(any())
            }.returns(newServerConfigEntity)

            return this
        }

        suspend fun withVersionApiResponse(): Arrangement {
            coEvery { versionApi.fetchApiVersion(any()) }
                .returns(NetworkResponse.Success(ServerConfigDTO.MetaData(true, ApiVersionDTO.fromInt(8), "wire.com"), mapOf(), 200))
            return this
        }

        fun withCalculateApiVersion(result: ServerConfigDTO.MetaData): Arrangement {
            every {
                backendMetaDataUtil.calculateApiVersion(any(), any(), any(), any())
            }.returns(result)
            return this
        }

        fun arrange() = this to customServerConfigRepository

    }

    companion object {
        val SERVER_CONFIG_URL = "https://test.test/test.json"
        val SERVER_CONFIG_RESPONSE = newServerConfigDTO(1)
        val SERVER_CONFIG = newServerConfig(1)
        val serverConfigEntity = newServerConfigEntity(1)
        val expectedServerConfig = newServerConfig(1).copy(
            metaData = ServerConfig.MetaData(
                commonApiVersion = CommonApiVersionType.Valid(5),
                federation = true,
                domain = serverConfigEntity.metaData.domain
            )
        )
    }
}
