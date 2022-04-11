package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
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
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class, ConfigurationApi::class)
class ServerConfigRepositoryTest {

    @Mock
    private val serverConfigRemoteRepository = mock(classOf<ServerConfigRemoteRepository>())

    @Mock
    private val serverConfigurationDAO = configure(mock(classOf<ServerConfigurationDAO>())) {
        stubsUnitByDefault = true
    }

    private lateinit var serverConfigRepository: ServerConfigRepository

    @BeforeTest
    fun setup() {
        serverConfigRepository = ServerConfigDataSource(serverConfigRemoteRepository, serverConfigurationDAO)
    }

    @Test
    fun givenUrl_whenFetchingServerConfigSuccess_thenTheSuccessIsReturned() = runTest {
        val serverConfigUrl = SERVER_CONFIG_URL
        val expected = Either.Right(SERVER_CONFIG)
        given(serverConfigRemoteRepository)
            .coroutine { serverConfigRepository.fetchRemoteConfig(serverConfigUrl) }
            .then { expected }

        val actual = serverConfigRepository.fetchRemoteConfig(serverConfigUrl)

        actual.shouldSucceed { expected.value }
        verify(serverConfigRemoteRepository)
            .coroutine { serverConfigRepository.fetchRemoteConfig(serverConfigUrl) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_whenDeleteByTitle_thenDAOFunctionIsCalled() {
        val title = "wire-test"
        val expected = Either.Right(Unit)
        given(serverConfigurationDAO).invocation { deleteByTitle(title) }

        val actual = serverConfigRepository.deleteByTitle(title)

        actual.shouldSucceed { expected.value }

        verify(serverConfigurationDAO)
            .function(serverConfigurationDAO::deleteByTitle)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_whenDelete_thenDAOFunctionIsCalled() {
        val serverConfig = SERVER_CONFIG
        val expected = Either.Right(Unit)
        given(serverConfigurationDAO).invocation { deleteByTitle(serverConfig.title) }

        val actual = serverConfigRepository.deleteByTitle(serverConfig.title)

        actual.shouldSucceed { expected.value }

        verify(serverConfigurationDAO)
            .function(serverConfigurationDAO::deleteByTitle)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_whenStoreConfigISInvoked_themDAOIsCalled() {
        val serverConfig = SERVER_CONFIG
        val expected = Either.Right(Unit)

        given(serverConfigurationDAO).invocation {
            with(serverConfig) {
                insert(apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, title)
            }
        }

        serverConfigRepository.storeConfig(serverConfig)
            .shouldSucceed { expected.value }

        verify(serverConfigurationDAO)
            .function(serverConfigurationDAO::insert)
            .with(any(), any(), any(), any(), any(), any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_whenConfigListISInvoked_themDAOIsCalled() {
        val expected = Either.Right(listOf(SERVER_CONFIG))
        given(serverConfigurationDAO).invocation { allConfig() }.then {
            with(expected.value.first()) {
                listOf(
                    ServerConfigEntity(
                        apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, title
                    )
                )
            }
        }
        serverConfigRepository.configList().shouldSucceed { expected.value }

        verify(serverConfigurationDAO)
            .function(serverConfigurationDAO::allConfig)
            .wasInvoked(exactly = once)
    }


    private companion object {
        val randomString get() = Random.nextBytes(64).decodeToString()

        const val SERVER_CONFIG_URL = "https://test.test/test.json"
        val SERVER_CONFIG = ServerConfig(randomString, randomString, randomString, randomString, randomString, randomString, randomString)
    }

}
