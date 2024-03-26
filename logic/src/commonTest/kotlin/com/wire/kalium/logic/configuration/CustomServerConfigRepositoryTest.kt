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
import com.wire.kalium.logic.configuration.server.CustomServerConfigDataSource
import com.wire.kalium.logic.configuration.server.CustomServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfigDTO
import com.wire.kalium.logic.util.stubs.newServerConfigEntity
import com.wire.kalium.network.BackendMetaDataUtil
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApi
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.model.ServerConfigEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
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
        val expected = arrangement.SERVER_CONFIG.links
        val serverConfigUrl = arrangement.SERVER_CONFIG_URL

        val actual = repository.fetchRemoteConfig(serverConfigUrl)

        actual.shouldSucceed { assertEquals(expected, it) }
        verify(arrangement.serverConfigApi)
            .coroutine { arrangement.serverConfigApi.fetchServerConfig(serverConfigUrl) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_whenAddingTheSameOneWithNewApiVersionParams_thenStoredOneShouldBeUpdatedAndReturned() = runTest {
        val (arrangement, repository) = Arrangement()
            .withUpdatedServerConfig()
            .arrange()

        repository
            .storeConfig(arrangement.expectedServerConfig.links, arrangement.expectedServerConfig.metaData)
            .shouldSucceed { assertEquals(arrangement.expectedServerConfig, it) }

        verify(arrangement.serverConfigurationDAO)
            .suspendFunction(arrangement.serverConfigurationDAO::configByLinks)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigurationDAO)
            .suspendFunction(arrangement.serverConfigurationDAO::insert)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.serverConfigurationDAO)
            .suspendFunction(arrangement.serverConfigurationDAO::updateApiVersion)
            .with(any(), any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigurationDAO)
            .suspendFunction(arrangement.serverConfigurationDAO::setFederationToTrue)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigurationDAO)
            .function(arrangement.serverConfigurationDAO::configById)
            .with(any())
            .wasInvoked(exactly = once)
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

        verify(arrangement.serverConfigurationDAO)
            .suspendFunction(arrangement.serverConfigurationDAO::configByLinks)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigurationDAO)
            .suspendFunction(arrangement.serverConfigurationDAO::insert)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigurationDAO)
            .suspendFunction(arrangement.serverConfigurationDAO::updateApiVersion)
            .with(any(), any())
            .wasNotInvoked()
        verify(arrangement.serverConfigurationDAO)
            .suspendFunction(arrangement.serverConfigurationDAO::setFederationToTrue)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.serverConfigurationDAO)
            .function(arrangement.serverConfigurationDAO::configById)
            .with(any())
            .wasInvoked(exactly = once)
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

        verify(arrangement.backendMetaDataUtil)
            .function(arrangement.backendMetaDataUtil::calculateApiVersion)
            .with(any(), any(), any(), any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigurationDAO)
            .suspendFunction(arrangement.serverConfigurationDAO::configByLinks)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigurationDAO)
            .suspendFunction(arrangement.serverConfigurationDAO::insert)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigurationDAO)
            .suspendFunction(arrangement.serverConfigurationDAO::updateApiVersion)
            .with(any(), any())
            .wasNotInvoked()
        verify(arrangement.serverConfigurationDAO)
            .suspendFunction(arrangement.serverConfigurationDAO::setFederationToTrue)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.serverConfigurationDAO)
            .function(arrangement.serverConfigurationDAO::configById)
            .with(any())
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        val SERVER_CONFIG_URL = "https://test.test/test.json"
        val SERVER_CONFIG_RESPONSE = newServerConfigDTO(1)
        val SERVER_CONFIG = newServerConfig(1)

        @Mock
        val serverConfigApi = mock(classOf<ServerConfigApi>())
        
        var developmentApiEnabled by Delegates.notNull<Boolean>()

        @Mock
        val serverConfigurationDAO = mock(classOf<ServerConfigurationDAO>())
        init {
            developmentApiEnabled = false
        }
        
        @Mock
        val backendMetaDataUtil = mock(classOf<BackendMetaDataUtil>())
        
        private var customServerConfigRepository: CustomServerConfigRepository =
            CustomServerConfigDataSource(serverConfigApi, developmentApiEnabled, serverConfigurationDAO, backendMetaDataUtil)

        val serverConfigEntity = newServerConfigEntity(1)
        val expectedServerConfig = newServerConfig(1).copy(
            metaData = ServerConfig.MetaData(
                commonApiVersion = CommonApiVersionType.Valid(5),
                federation = true,
                domain = serverConfigEntity.metaData.domain
            )
        )
        
        

        suspend fun withConfigForNewRequest(serverConfigEntity: ServerConfigEntity): Arrangement {
            given(serverConfigurationDAO)
                .coroutine { configByLinks(serverConfigEntity.links) }
                .then { null }
            given(serverConfigurationDAO)
                .function(serverConfigurationDAO::configById)
                .whenInvokedWith(any())
                .then { newServerConfigEntity(1) }
            return this
        }

        suspend fun withSuccessConfigResponse(): Arrangement {
            given(serverConfigApi)
                .coroutine { serverConfigApi.fetchServerConfig(SERVER_CONFIG_URL) }
                .then { NetworkResponse.Success(SERVER_CONFIG_RESPONSE.links, mapOf(), 200) }
            return this
        }

        suspend fun withDaoEntityResponse(): Arrangement {
            given(serverConfigurationDAO).coroutine { allConfig() }
                .then { listOf(newServerConfigEntity(1), newServerConfigEntity(2), newServerConfigEntity(3)) }
            return this
        }

        fun withConfigById(serverConfig: ServerConfigEntity): Arrangement {
            given(serverConfigurationDAO)
                .function(serverConfigurationDAO::configById)
                .whenInvokedWith(any())
                .then { serverConfig }
            return this
        }

        fun withConfigByLinks(serverConfigEntity: ServerConfigEntity?): Arrangement {
            given(serverConfigurationDAO)
                .suspendFunction(serverConfigurationDAO::configByLinks)
                .whenInvokedWith(any())
                .thenReturn(serverConfigEntity)
            return this
        }

        suspend fun withDaoEntityFlowResponse(): Arrangement {
            given(serverConfigurationDAO).coroutine { allConfigFlow() }
                .then { flowOf(listOf(newServerConfigEntity(1), newServerConfigEntity(2), newServerConfigEntity(3))) }
            return this
        }
        
        suspend fun withUpdatedServerConfig(): Arrangement {
            val newServerConfigEntity = serverConfigEntity.copy(
                metaData = serverConfigEntity.metaData.copy(
                    apiVersion = 5,
                    federation = true
                )
            )

            given(serverConfigurationDAO)
                .coroutine { configByLinks(serverConfigEntity.links) }
                .then { serverConfigEntity }
            given(serverConfigurationDAO)
                .function(serverConfigurationDAO::configById)
                .whenInvokedWith(any())
                .then { newServerConfigEntity }

            return this
        }

        fun withCalculateApiVersion(result: ServerConfigDTO.MetaData): Arrangement {
            given(backendMetaDataUtil)
                .function(backendMetaDataUtil::calculateApiVersion)
                .whenInvokedWith(any(), any(), any(), any())
                .thenReturn(result)
            return this
        }

        fun arrange() = this to customServerConfigRepository

    }
}
