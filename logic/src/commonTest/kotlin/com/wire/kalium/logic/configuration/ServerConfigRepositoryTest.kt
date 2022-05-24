package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigDataSource
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfigUtil
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfigDTO
import com.wire.kalium.logic.util.stubs.newServerConfigEntity
import com.wire.kalium.logic.util.stubs.newServerConfigResponse
import com.wire.kalium.network.api.configuration.ServerConfigApi
import com.wire.kalium.network.api.configuration.ServerConfigResponse
import com.wire.kalium.network.api.versioning.VersionApi
import com.wire.kalium.network.api.versioning.VersionInfoDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import com.wire.kalium.persistence.model.ServerConfigEntity
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

    @Mock
    private val serverConfigUtil = mock(classOf<ServerConfigUtil>())

    private lateinit var serverConfigRepository: ServerConfigRepository

    @BeforeTest
    fun setup() {
        serverConfigRepository = ServerConfigDataSource(serverConfigApi, serverConfigDAO, versionApi, serverConfigUtil)
    }

    @Test
    fun givenUrl_whenFetchingServerConfigSuccess_thenTheSuccessIsReturned() = runTest {
        val serverConfigUrl = SERVER_CONFIG_URL
        val expected = NetworkResponse.Success(SERVER_CONFIG_RESPONSE, mapOf(), 200)
        given(serverConfigApi)
            .coroutine { serverConfigApi.fetchServerConfig(serverConfigUrl) }
            .then { expected }

        val actual = serverConfigRepository.fetchRemoteConfig(serverConfigUrl)

        actual.shouldSucceed { assertEquals(it, expected.value) }
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
        val testConfigResponse = newServerConfigResponse(1)
        val expected = newServerConfig(1)
        val versionInfoDTO = VersionInfoDTO(expected.domain, expected.federation, listOf(1, 2))
        given(versionApi)
            .suspendFunction(versionApi::fetchApiVersion)
            .whenInvokedWith(any())
            .then { NetworkResponse.Success(versionInfoDTO, mapOf(), 200) }

        given(serverConfigUtil)
            .invocation { calculateApiVersion(versionInfoDTO.supported) }
            .then { Either.Right(expected.commonApiVersion.version) }

        given(serverConfigDAO)
            .function(serverConfigDAO::configById)
            .whenInvokedWith(any())
            .then { newServerConfigEntity(1) }

        given(serverConfigDAO)
            .function(serverConfigDAO::configByUniqueFields)
            .whenInvokedWith(any(), any(), any(), any())
            .thenReturn(null)

        serverConfigRepository.fetchApiVersionAndStore(testConfigResponse).shouldSucceed {
            assertEquals(expected, it)
        }

        verify(versionApi)
            .suspendFunction(versionApi::fetchApiVersion)
            .with(any())
            .wasInvoked(exactly = once)
        verify(serverConfigUtil)
            .function(serverConfigUtil::calculateApiVersion)
            .with(any(), any())
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
        val serverConfig = newServerConfigEntity(1)
        val newServerConfig = newServerConfigEntity(1).copy(commonApiVersion = newApiVersion, federation = newFederation)
        val expected = newServerConfig(1).copy(commonApiVersion = CommonApiVersionType.Valid(newApiVersion), federation = newFederation)

        given(serverConfigDAO)
            .invocation { with(serverConfig) { configByUniqueFields(title, apiBaseUrl, webSocketBaseUrl, domain) } }
            .then { serverConfig }
        given(serverConfigDAO)
            .function(serverConfigDAO::configById)
            .whenInvokedWith(any())
            .then { newServerConfig }

        serverConfigRepository
            .storeConfig(newServerConfigResponse(1), serverConfig.domain, newApiVersion, newFederation)
            .shouldSucceed { assertEquals(it, expected) }

        verify(serverConfigDAO)
            .function(serverConfigDAO::configByUniqueFields)
            .with(any(), any(), any(), any())
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
            .invocation { with(serverConfig) { configByUniqueFields(title, apiBaseUrl, webSocketBaseUrl, domain) } }
            .then { null }
        given(serverConfigDAO)
            .function(serverConfigDAO::configById)
            .whenInvokedWith(any())
            .then { serverConfig }

        serverConfigRepository
            .storeConfig(newServerConfigResponse(1), serverConfig.domain, serverConfig.commonApiVersion, serverConfig.federation)
            .shouldSucceed { assertEquals(it, expected) }

        verify(serverConfigDAO)
            .function(serverConfigDAO::configByUniqueFields)
            .with(any(), any(), any(), any())
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
        val SERVER_CONFIG_RESPONSE = newServerConfigResponse(1)
        val SERVER_CONFIG = newServerConfig(1)
        val SERVER_CONFIG_DTO = newServerConfigDTO(1)
    }

}
