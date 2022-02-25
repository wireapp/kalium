package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
class ServerConfigRepositoryTest {

    @Mock
    private val serverConfigRemoteRepository = mock(classOf<ServerConfigRemoteRepository>())

    private lateinit var serverConfigRepository: ServerConfigRepository

    @BeforeTest
    fun setup() {
        serverConfigRepository = ServerConfigDataSource(serverConfigRemoteRepository)
    }

    // serverConfig
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

    private companion object {
        val randomString get() = Random.nextBytes(64).decodeToString()

        const val SERVER_CONFIG_URL = "https://test.test/test.json"
        val SERVER_CONFIG = ServerConfig(randomString, randomString, randomString, randomString, randomString, randomString, randomString)
    }

}
