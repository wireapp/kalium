package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigDataSource
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfigDTO
import com.wire.kalium.logic.util.stubs.newServerConfigEntity
import com.wire.kalium.network.api.configuration.ServerConfigApi
import com.wire.kalium.network.api.versioning.VersionApi
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import io.mockative.ConfigurationApi
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ConfigurationApi::class)
@ExperimentalCoroutinesApi
class ServerConfigRepositoryTest {

    @Mock
    private val serverConfigApi = mock(classOf<ServerConfigApi>())

    @Mock
    private val serverConfigDAO = configure(mock(classOf<ServerConfigurationDAO>())) {
        stubsUnitByDefault = true
    }

    @Mock
    private val versionApi = mock(classOf<VersionApi>())

    private lateinit var serverConfigRepository: ServerConfigRepository

    @BeforeTest
    fun setup() {
        serverConfigRepository = ServerConfigDataSource(serverConfigApi, serverConfigDAO, versionApi)
    }

    @Test
    fun givenUrl_whenFetchingServerConfigSuccess_thenTheSuccessIsReturned() = runTest {
        val serverConfigUrl = SERVER_CONFIG_URL
        val expected = SERVER_CONFIG.links
        given(serverConfigApi)
            .coroutine { serverConfigApi.fetchServerConfig(serverConfigUrl) }
            .then { NetworkResponse.Success(SERVER_CONFIG_RESPONSE.links, mapOf(), 200) }

        val actual = serverConfigRepository.fetchRemoteConfig(serverConfigUrl)

        actual.shouldSucceed { assertEquals(expected, it) }
        verify(serverConfigApi)
            .coroutine { serverConfigApi.fetchServerConfig(serverConfigUrl) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_thenItCanBeRetrievedAsList() {
        val expected = listOf(newServerConfig(1), newServerConfig(2), newServerConfig(3))
        given(serverConfigDAO).invocation { allConfig() }
            .then { listOf(newServerConfigEntity(1), newServerConfigEntity(2), newServerConfigEntity(3)) }

        serverConfigRepository.configList().shouldSucceed { assertEquals(it, expected) }

        verify(serverConfigDAO).function(serverConfigDAO::allConfig).wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_thenItCanBeRetrievedAsFlow() = runTest {
        val expected = flowOf(listOf(newServerConfig(1), newServerConfig(2), newServerConfig(3)))
        given(serverConfigDAO).invocation { allConfigFlow() }
            .then { flowOf(listOf(newServerConfigEntity(1), newServerConfigEntity(2), newServerConfigEntity(3))) }

        val actual = serverConfigRepository.configFlow()

        assertIs<Either.Right<Flow<List<ServerConfig>>>>(actual)
        assertEquals(expected.first(), actual.value.first())

        verify(serverConfigDAO).function(serverConfigDAO::allConfigFlow).wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_thenItCanBeRetrievedById() {
        val serverConfig = newServerConfigEntity(1)
        val expected = newServerConfig(1)
        given(serverConfigDAO).invocation { configById(serverConfig.id) }.then { serverConfig }

        val actual = serverConfigRepository.configById(serverConfig.id)
        assertIs<Either.Right<ServerConfig>>(actual)
        assertEquals(expected, actual.value)

        verify(serverConfigDAO).function(serverConfigDAO::configById).with(any()).wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_thenItCanBeDeleted() {
        val serverConfig = SERVER_CONFIG
        given(serverConfigDAO).invocation { deleteById(serverConfig.id) }

        val actual = serverConfigRepository.deleteById(serverConfig.id)

        actual.shouldSucceed()

        verify(serverConfigDAO).function(serverConfigDAO::deleteById).with(any()).wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_whenDeleting_thenItCanBeDeleted() {
        val serverConfig = SERVER_CONFIG
        given(serverConfigDAO).invocation { deleteById(serverConfig.id) }

        val actual = serverConfigRepository.delete(serverConfig)

        actual.shouldSucceed()

        verify(serverConfigDAO).function(serverConfigDAO::deleteById).with(any()).wasInvoked(exactly = once)
    }

    @Test
    fun givenValidCompatibleApiVersion_whenStoringConfigLocally_thenConfigIsStored() = runTest {
        val testConfigResponse = newServerConfig(1).links
        val expected = newServerConfig(1)
        val versionInfoDTO = ServerConfigDTO.MetaData(
            domain = expected.metaData.domain,
            federation = expected.metaData.federation,
            commonApiVersion = ApiVersionDTO.Valid(1)
        )
        given(versionApi)
            .suspendFunction(versionApi::fetchApiVersion)
            .whenInvokedWith(any())
            .then { NetworkResponse.Success(versionInfoDTO, mapOf(), 200) }


        given(serverConfigDAO)
            .function(serverConfigDAO::configById)
            .whenInvokedWith(any())
            .then { newServerConfigEntity(1) }

        given(serverConfigDAO)
            .function(serverConfigDAO::configByLinks)
            .whenInvokedWith(any(), any(), any())
            .thenReturn(null)

        serverConfigRepository.fetchApiVersionAndStore(testConfigResponse).shouldSucceed {
            assertEquals(expected, it)
        }

        verify(versionApi)
            .suspendFunction(versionApi::fetchApiVersion)
            .with(any())
            .wasInvoked(exactly = once)

        verify(serverConfigDAO)
            .function(serverConfigDAO::configById)
            .with(any())
            .wasInvoked(exactly = once)
    }


    @Test
    fun givenStoredConfig_whenAddingTheSameOneWithNewApiVersionParams_thenStoredOneShouldBeUpdatedAndReturned() {
        val newApiVersion = 5
        val newFederation = true
        val serverConfigEntity = newServerConfigEntity(1)
        val newServerConfigEntity = serverConfigEntity.copy(
            metaData = serverConfigEntity.metaData.copy(
                apiVersion = newApiVersion,
                federation = newFederation
            )
        )
        val expected = newServerConfig(1).copy(
            metaData = ServerConfig.MetaData(
                commonApiVersion = CommonApiVersionType.Valid(newApiVersion),
                federation = newFederation,
                domain = serverConfigEntity.metaData.domain
            )
        )

        given(serverConfigDAO)
            .invocation { with(serverConfigEntity) { configByLinks(links.title, links.api, links.webSocket) } }
            .then { serverConfigEntity }
        given(serverConfigDAO)
            .function(serverConfigDAO::configById)
            .whenInvokedWith(any())
            .then { newServerConfigEntity }

        serverConfigRepository
            .storeConfig(expected.links, expected.metaData)
            .shouldSucceed { assertEquals(expected, it) }

        verify(serverConfigDAO)
            .function(serverConfigDAO::configByLinks)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)
        verify(serverConfigDAO)
            .function(serverConfigDAO::insert)
            .with(any())
            .wasNotInvoked()
        verify(serverConfigDAO)
            .function(serverConfigDAO::updateApiVersion)
            .with(any(), any())
            .wasInvoked(exactly = once)
        verify(serverConfigDAO)
            .function(serverConfigDAO::setFederationToTrue)
            .with(any())
            .wasInvoked(exactly = once)
        verify(serverConfigDAO)
            .function(serverConfigDAO::configById)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_whenAddingNewOne_thenNewOneShouldBeInsertedAndReturned() {
        val serverConfig = newServerConfigEntity(1)
        val expected = newServerConfig(1)

        given(serverConfigDAO)
            .invocation { with(serverConfig) { configByLinks(links.title, links.api, links.webSocket) } }
            .then { null }
        given(serverConfigDAO)
            .function(serverConfigDAO::configById)
            .whenInvokedWith(any())
            .then { serverConfig }
        // config is not inserted before
        given(serverConfigDAO)
            .invocation { configByLinks(serverConfig.links.title, serverConfig.links.api, serverConfig.links.webSocket) }
            .then { null }

        serverConfigRepository
            .storeConfig(expected.links, expected.metaData)
            .shouldSucceed { assertEquals(it, expected) }

        verify(serverConfigDAO)
            .function(serverConfigDAO::configByLinks)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)
        verify(serverConfigDAO)
            .function(serverConfigDAO::insert)
            .with(any())
            .wasInvoked(exactly = once)
        verify(serverConfigDAO)
            .function(serverConfigDAO::updateApiVersion)
            .with(any(), any())
            .wasNotInvoked()
        verify(serverConfigDAO)
            .function(serverConfigDAO::setFederationToTrue)
            .with(any())
            .wasNotInvoked()
        verify(serverConfigDAO)
            .function(serverConfigDAO::configById)
            .with(any())
            .wasInvoked(exactly = once)

    }

    private companion object {
        const val SERVER_CONFIG_URL = "https://test.test/test.json"
        val SERVER_CONFIG_RESPONSE = newServerConfigDTO(1)
        val SERVER_CONFIG = newServerConfig(1)
    }
}
