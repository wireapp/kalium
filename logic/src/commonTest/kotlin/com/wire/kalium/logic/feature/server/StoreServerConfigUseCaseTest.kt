package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.Mock
import io.mockative.eq
import io.mockative.fun2
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class StoreServerConfigUseCaseTest {

    @Test
    fun givenServerConfigRepository_whenStoreConfig_thenSuccess() = runTest {
        val links = newServerConfig(1).links
        val versionInfo = ServerConfig.VersionInfo(false, listOf(1), null, listOf(2))
        val expected = newServerConfig(1)

        val (arrangement, useCase) = Arrangement()
            .withStoreConfig(expected.links, versionInfo, Either.Right(expected))
            .arrange()

        useCase(links, versionInfo).also {
            assertIs<StoreServerConfigResult.Success>(it)
            assertEquals(expected, it.serverConfig)
        }

        verify(arrangement.configRepository)
            .coroutine { arrangement.configRepository.storeConfig(links, versionInfo) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenServerlinkswithAwaitAtTheEndOfWebSocketLink_whenStoreConfig_thenStoreWithAwaitRemoved() = runTest {
        val links = newServerConfig(1).links.copy(webSocket = "wss://example.com/await")
        val versionInfo = ServerConfig.VersionInfo(false, listOf(1), null, listOf(2))
        val expected = newServerConfig(1).copy(links = links.copy(webSocket = "wss://example.com/"))

        val (arrangement, useCase) = Arrangement()
            .withStoreConfig(expected.links, versionInfo, Either.Right(expected))
            .arrange()

        useCase(links, versionInfo).also {
            assertIs<StoreServerConfigResult.Success>(it)
            assertEquals(expected, it.serverConfig)
        }

        verify(arrangement.configRepository)
            .coroutine { arrangement.configRepository.storeConfig(expected.links, versionInfo) }
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val configRepository = mock(ServerConfigRepository::class)
        private val useCase = StoreServerConfigUseCaseImpl(configRepository)

        suspend fun withStoreConfig(
            links: ServerConfig.Links,
            versionInfo: ServerConfig.VersionInfo,
            result: Either<StorageFailure, ServerConfig>
        ) = apply {
            given(configRepository)
                .function(
                    configRepository::storeConfig,
                    fun2<ServerConfig.Links, ServerConfig.VersionInfo>())
                .whenInvokedWith(eq(links), eq(versionInfo))
                .thenReturn(result)
        }

        fun arrange() = this to useCase
    }

}
