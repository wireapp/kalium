package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigDataSource
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfigDTO
import com.wire.kalium.logic.util.stubs.newServerConfigEntity
import com.wire.kalium.network.BackendMetaDataUtil
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApi
import com.wire.kalium.network.api.base.unbound.versioning.VersionApi
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.model.ServerConfigEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@ExperimentalCoroutinesApi
class ServerConfigRepositoryTest {

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
    fun givenStoredConfig_thenItCanBeRetrievedAsList() = runTest {
        val (arrangement, repository) = Arrangement().withDaoEntityResponse().arrange()
        val expected = listOf(newServerConfig(1), newServerConfig(2), newServerConfig(3))

        repository.configList().shouldSucceed { assertEquals(it, expected) }
        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::allConfig)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_thenItCanBeRetrievedAsFlow() = runTest {
        val (arrangement, repository) = Arrangement().withDaoEntityFlowResponse().arrange()
        val expected = listOf(newServerConfig(1), newServerConfig(2), newServerConfig(3))

        val actual = repository.configFlow()

        assertIs<Either.Right<Flow<List<ServerConfig>>>>(actual)
        assertEquals(expected.first(), actual.value.first()[0])

        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::allConfigFlow)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_thenItCanBeRetrievedById() = runTest {
        val (arrangement, repository) = Arrangement()
            .withConfigById(newServerConfigEntity(1))
            .arrange()
        val expected = newServerConfig(1)

        val actual = repository.configById(expected.id)
        assertIs<Either.Right<ServerConfig>>(actual)
        assertEquals(expected, actual.value)

        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::configById)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_thenItCanBeDeleted() = runTest {
        val serverConfigId = "1"
        val (arrangement, repository) = Arrangement()
            .withConfigById(newServerConfigEntity(1))
            .arrange()

        val actual = repository.deleteById(serverConfigId)

        actual.shouldSucceed()
        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::deleteById)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_whenDeleting_thenItCanBeDeleted() = runTest {
        val serverConfig = newServerConfig(1)
        val (arrangement, repository) = Arrangement()
            .withConfigById(newServerConfigEntity(1))
            .arrange()

        val actual = repository.delete(serverConfig)

        actual.shouldSucceed()
        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::deleteById)
            .with(any())
            .wasInvoked(exactly = once)
    }

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

        verify(arrangement.versionApi)
            .suspendFunction(arrangement.versionApi::fetchApiVersion)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::configById)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::insert)
            .with(any())
            .wasInvoked(exactly = once)
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

        verify(arrangement.versionApi)
            .suspendFunction(arrangement.versionApi::fetchApiVersion)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::configById)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::configByLinks)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::insert)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenStoredConfig_whenAddingTheSameOneWithNewApiVersionParams_thenStoredOneShouldBeUpdatedAndReturned() = runTest {
        val (arrangement, repository) = Arrangement()
            .withUpdatedServerConfig()
            .arrange()

        repository
            .storeConfig(arrangement.expectedServerConfig.links, arrangement.expectedServerConfig.metaData)
            .shouldSucceed { assertEquals(arrangement.expectedServerConfig, it) }

        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::configByLinks)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::insert)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::updateApiVersion)
            .with(any(), any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::setFederationToTrue)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::configById)
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

        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::configByLinks)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::insert)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::updateApiVersion)
            .with(any(), any())
            .wasNotInvoked()
        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::setFederationToTrue)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::configById)
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
        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::configByLinks)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::insert)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::updateApiVersion)
            .with(any(), any())
            .wasNotInvoked()
        verify(arrangement.serverConfigDAO)
            .suspendFunction(arrangement.serverConfigDAO::setFederationToTrue)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::configById)
            .with(any())
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        val SERVER_CONFIG_URL = "https://test.test/test.json"
        val SERVER_CONFIG_RESPONSE = newServerConfigDTO(1)
        val SERVER_CONFIG = newServerConfig(1)

        @Mock
        val serverConfigApi = mock(classOf<ServerConfigApi>())

        @Mock
        val serverConfigDAO = configure(mock(classOf<ServerConfigurationDAO>())) {
            stubsUnitByDefault = true
        }

        @Mock
        val versionApi = mock(classOf<VersionApi>())

        @Mock
        val backendMetaDataUtil = mock(classOf<BackendMetaDataUtil>())

        private var serverConfigRepository: ServerConfigRepository =
            ServerConfigDataSource(serverConfigApi, serverConfigDAO, versionApi, true, backendMetaDataUtil)

        val serverConfigEntity = newServerConfigEntity(1)
        val expectedServerConfig = newServerConfig(1).copy(
            metaData = ServerConfig.MetaData(
                commonApiVersion = CommonApiVersionType.Valid(5),
                federation = true,
                domain = serverConfigEntity.metaData.domain
            )
        )

        suspend fun withConfigForNewRequest(serverConfigEntity: ServerConfigEntity): Arrangement {
            given(serverConfigDAO)
                .coroutine { configByLinks(serverConfigEntity.links) }
                .then { null }
            given(serverConfigDAO)
                .function(serverConfigDAO::configById)
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
            given(serverConfigDAO).coroutine { allConfig() }
                .then { listOf(newServerConfigEntity(1), newServerConfigEntity(2), newServerConfigEntity(3)) }
            return this
        }

        fun withConfigById(serverConfig: ServerConfigEntity): Arrangement {
            given(serverConfigDAO)
                .function(serverConfigDAO::configById)
                .whenInvokedWith(any())
                .then { serverConfig }
            return this
        }

        fun withConfigByLinks(serverConfigEntity: ServerConfigEntity?): Arrangement {
            given(serverConfigDAO)
                .suspendFunction(serverConfigDAO::configByLinks)
                .whenInvokedWith(any())
                .thenReturn(serverConfigEntity)
            return this
        }

        suspend fun withDaoEntityFlowResponse(): Arrangement {
            given(serverConfigDAO).coroutine { allConfigFlow() }
                .then { flowOf(listOf(newServerConfigEntity(1), newServerConfigEntity(2), newServerConfigEntity(3))) }
            return this
        }

        fun withApiAversionResponse(serverConfigDTO: ServerConfigDTO.MetaData): Arrangement {
            given(versionApi)
                .suspendFunction(versionApi::fetchApiVersion)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(serverConfigDTO, mapOf(), 200) }

            return this
        }

        suspend fun withUpdatedServerConfig(): Arrangement {
            val newServerConfigEntity = serverConfigEntity.copy(
                metaData = serverConfigEntity.metaData.copy(
                    apiVersion = 5,
                    federation = true
                )
            )

            given(serverConfigDAO)
                .coroutine { configByLinks(serverConfigEntity.links) }
                .then { serverConfigEntity }
            given(serverConfigDAO)
                .function(serverConfigDAO::configById)
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

        fun arrange() = this to serverConfigRepository

    }
}
