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
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfigDTO
import com.wire.kalium.logic.util.stubs.newServerConfigEntity
import com.wire.kalium.network.api.base.unbound.versioning.VersionApi
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.model.ServerConfigEntity
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class ServerConfigRepositoryTest {

    private lateinit var testDispatcher: TestDispatcher

    @BeforeTest
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun breakDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenValidCompatibleApiVersion_whenStoringConfigLocally_thenConfigIsStored() = runTest {
        val expected = newServerConfig(1)
        val expectedDTO = newServerConfigDTO(1)

        val expectedEntity = newServerConfigEntity(1)
        val (arrangement, repository) = Arrangement(testDispatcher.testKaliumDispatcher())
            .withApiAversionResponse(expectedDTO.metaData)
            .withConfigById(expectedEntity)
            .withConfigByLinks(null)
            .arrange()

        repository.fetchApiVersionAndStore(expected.links).shouldSucceed {
            assertEquals(expected, it)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.versionApi.fetchApiVersion(any())
        }

        verify(VerifyMode.exactly(1)) {
            arrangement.serverConfigDAO.configById(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serverConfigDAO.insert(any())
        }
    }

    @Test
    fun givenInValidCompatibleApiVersion_whenStoringConfigLocally_thenErrorIsPropagated() = runTest {
        val expected = newServerConfig(1).copy(metaData = ServerConfig.MetaData(false, CommonApiVersionType.Unknown, "domain"))
        val expectedMetaDataDTO = ServerConfigDTO.MetaData(false, ApiVersionDTO.Invalid.Unknown, "domain")
        val expectedEntity = newServerConfigEntity(1).copy(metaData = ServerConfigEntity.MetaData(false, -2, "domain"))

        val (arrangement, repository) = Arrangement(testDispatcher.testKaliumDispatcher())
            .withApiAversionResponse(expectedMetaDataDTO)
            .withConfigByLinks(expectedEntity)
            .arrange()

        repository.fetchApiVersionAndStore(expected.links).shouldFail() {
            assertEquals(ServerConfigFailure.UnknownServerVersion, it)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.versionApi.fetchApiVersion(any())
        }

        verify(VerifyMode.not) {
            arrangement.serverConfigDAO.configById(any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.serverConfigDAO.configByLinks(any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.serverConfigDAO.insert(any())
        }
    }

    @Test
    fun givenStoredConfig_whenAddingTheSameOneWithNewApiVersionParams_thenStoredOneShouldBeUpdatedAndReturned() = runTest {
        val (arrangement, repository) = Arrangement(testDispatcher.testKaliumDispatcher())
            .withUpdatedServerConfig()
            .arrange()

        repository
            .storeConfig(arrangement.expectedServerConfig.links, arrangement.expectedServerConfig.metaData)
            .shouldSucceed { assertEquals(arrangement.expectedServerConfig, it) }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serverConfigDAO.configByLinks(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.serverConfigDAO.insert(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serverConfigDAO.updateServerMetaData(any(), any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serverConfigDAO.setFederationToTrue(any())
        }
        verify(VerifyMode.exactly(1)) {
            arrangement.serverConfigDAO.configById(any())
        }
    }

    @Test
    fun givenStoredConfig_whenAddingNewOne_thenNewOneShouldBeInsertedAndReturned() = runTest {
        val expected = newServerConfig(1)
        val (arrangement, repository) = Arrangement(testDispatcher.testKaliumDispatcher())
            .withConfigForNewRequest(newServerConfigEntity(1))
            .arrange()

        repository
            .storeConfig(expected.links, expected.metaData)
            .shouldSucceed { assertEquals(it, expected) }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serverConfigDAO.configByLinks(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serverConfigDAO.insert(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.serverConfigDAO.updateServerMetaData(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.serverConfigDAO.setFederationToTrue(any())
        }
        verify(VerifyMode.exactly(1)) {
            arrangement.serverConfigDAO.configById(any())
        }
    }

    private class Arrangement(dispatcher: KaliumDispatcher) {

        val serverConfigDAO = mock<ServerConfigurationDAO>(mode = MockMode.autoUnit)
        val versionApi = mock<VersionApi>()

        private var serverConfigRepository: ServerConfigRepository =
            ServerConfigDataSource(serverConfigDAO, versionApi, dispatchers = dispatcher)

        val serverConfigEntity = newServerConfigEntity(1)
        val expectedServerConfig = newServerConfig(1).copy(
            metaData = ServerConfig.MetaData(
                commonApiVersion = CommonApiVersionType.Valid(5),
                federation = true,
                domain = serverConfigEntity.metaData.domain
            )
        )

        suspend fun withConfigForNewRequest(serverConfigEntity: ServerConfigEntity): Arrangement {
            everySuspend { serverConfigDAO.configByLinks(serverConfigEntity.links) } returns null
            every {
                serverConfigDAO.configById(any())
            } returns newServerConfigEntity(1)
            return this
        }

        fun withConfigById(serverConfig: ServerConfigEntity): Arrangement {
            every {
                serverConfigDAO.configById(any())
            } returns serverConfig
            return this
        }

        suspend fun withConfigByLinks(serverConfigEntity: ServerConfigEntity?): Arrangement {
            everySuspend {
                serverConfigDAO.configByLinks(any())
            } returns serverConfigEntity
            return this
        }

        suspend fun withApiAversionResponse(serverConfigDTO: ServerConfigDTO.MetaData): Arrangement {
            everySuspend {
                versionApi.fetchApiVersion(any())
            } returns NetworkResponse.Success(serverConfigDTO, mapOf(), 200)

            return this
        }

        suspend fun withUpdatedServerConfig(): Arrangement {
            val newServerConfigEntity = serverConfigEntity.copy(
                metaData = serverConfigEntity.metaData.copy(
                    apiVersion = 5,
                    federation = true
                )
            )

            everySuspend {
                serverConfigDAO.configByLinks(serverConfigEntity.links)
            } returns serverConfigEntity
            every {
                serverConfigDAO.configById(any())
            } returns (
                newServerConfigEntity
            )

            return this
        }

        fun arrange() = this to serverConfigRepository

    }
}
