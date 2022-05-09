package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigDataSource
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfigDTO
import com.wire.kalium.logic.util.stubs.newServerConfigEntity
import com.wire.kalium.network.api.api_version.VersionApi
import com.wire.kalium.network.api.api_version.VersionInfoDTO
import com.wire.kalium.network.api.configuration.ServerConfigApi
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
        val expected = NetworkResponse.Success(SERVER_CONFIG_DTO, mapOf(), 200)
        given(serverConfigApi)
            .coroutine { serverConfigApi.fetchServerConfig(serverConfigUrl) }
            .then { expected }

        val actual = serverConfigRepository.fetchRemoteConfig(serverConfigUrl)

        actual.shouldSucceed { expected.value }
        verify(serverConfigApi)
            .coroutine { serverConfigApi.fetchServerConfig(serverConfigUrl) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_thenItCanBeRetrievedAsList() {
        val expected = listOf(newServerConfig(1), newServerConfig(2), newServerConfig(3))
        given(serverConfigDAO).invocation { allConfig() }
            .then { listOf(newServerConfigEntity(1), newServerConfigEntity(2), newServerConfigEntity(3)) }

        serverConfigRepository.configList().shouldSucceed { Either.Right(expected) }

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
    fun givenSuccess_whenFetchingServerVersionInfo_thenSuccessIsPropagated() = runTest {
        val serverConfigDto = SERVER_CONFIG_DTO
        val expected = VersionInfoDTO("wire.com", true, listOf(0, 1, 2))
        given(versionApi)
            .coroutine { fetchApiVersion(serverConfigDto.apiBaseUrl) }
            .then { NetworkResponse.Success(expected, mapOf(), 200) }

        serverConfigRepository.fetchRemoteApiVersion(serverConfigDto).shouldSucceed { actual ->
            assertEquals(expected, actual)
        }
    }

    @Test
    fun givenError_whenFetchingServerVersionInfo_thenFailureIsPropagated() = runTest {
        val serverConfigDto = SERVER_CONFIG_DTO
        val expected = NetworkResponse.Error(TestNetworkException.generic)
        given(versionApi)
            .coroutine { fetchApiVersion(serverConfigDto.apiBaseUrl) }
            .then { NetworkResponse.Error(expected.kException) }

        serverConfigRepository.fetchRemoteApiVersion(serverConfigDto).shouldFail {actual ->
            assertIs<NetworkFailure.ServerMiscommunication>(actual)
            assertEquals(expected.kException, actual.kaliumException)
        }
    }


    private companion object {

        const val SERVER_CONFIG_URL = "https://test.test/test.json"
        val SERVER_CONFIG = newServerConfig(1)
        val SERVER_CONFIG_DTO = newServerConfigDTO(1)
    }

}
