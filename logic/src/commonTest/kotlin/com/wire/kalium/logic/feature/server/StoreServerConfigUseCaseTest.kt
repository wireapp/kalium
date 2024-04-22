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

package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.server.CustomServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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

        coVerify {
            arrangement.customServerConfigRepository.storeConfig(links, versionInfo)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenServerLinksWithAwaitAtTheEndOfWebSocketLink_whenStoreConfig_thenStoreWithAwaitRemoved() = runTest {
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

        coVerify {
            arrangement.customServerConfigRepository.storeConfig(expected.links, versionInfo)
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val customServerConfigRepository = mock(CustomServerConfigRepository::class)
        private val useCase = StoreServerConfigUseCaseImpl(customServerConfigRepository)

        suspend fun withStoreConfig(
            links: ServerConfig.Links,
            versionInfo: ServerConfig.VersionInfo,
            result: Either<StorageFailure, ServerConfig>
        ) = apply {
            coEvery { customServerConfigRepository.storeConfig(eq(links), eq(versionInfo)) }
                .returns(result)
        }

        fun arrange() = this to useCase
    }

}
