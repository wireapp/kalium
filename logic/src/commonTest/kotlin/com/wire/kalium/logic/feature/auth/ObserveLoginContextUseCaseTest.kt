/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.auth

import app.cash.turbine.test
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.server.CustomServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveLoginContextUseCaseTest {

    @Test
    fun givenServerConfig_whenInvokedAndAPILessThan8_thenReturnFallback() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withObserveServerConfigByLinks(flowOf(newServerConfig(1).copy(links = ServerConfig.DUMMY).right()))
            .arrange()

        val result = useCase(ServerConfig.DUMMY)

        result.test {
            assertEquals(LoginContext.FallbackLogin, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
        coVerify { arrangement.serverConfigRepository.observeServerConfigByLinks(eq(ServerConfig.DUMMY)) }
    }

    @Test
    fun givenServerConfig_whenInvokedAndProxyNeedsAuth_thenReturnFallback() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withObserveServerConfigByLinks(
                flowOf(
                    newServerConfig(1).copy(
                        metaData = ServerConfig.MetaData(
                            commonApiVersion = com.wire.kalium.logic.configuration.server.CommonApiVersionType.Valid(7),
                            domain = "domain",
                            federation = false
                        ),
                        links = ServerConfig.DUMMY.copy(
                            apiProxy = ServerConfig.ApiProxy(true, "dummy", 8080)
                        )
                    ).right()
                )
            )
            .arrange()

        val result = useCase(ServerConfig.DUMMY)

        result.test {
            assertEquals(LoginContext.FallbackLogin, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
        coVerify { arrangement.serverConfigRepository.observeServerConfigByLinks(eq(ServerConfig.DUMMY)) }
    }

    @Test
    fun givenServerConfig_whenInvokedAndAPIEqualsOrGreaterThan8_thenReturnEnterpriseLogin() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withObserveServerConfigByLinks(
                flowOf(
                    newServerConfig(1).copy(
                        metaData = ServerConfig.MetaData(
                            commonApiVersion = com.wire.kalium.logic.configuration.server.CommonApiVersionType.Valid(8),
                            domain = "domain",
                            federation = false
                        )
                    ).right()
                )
            )
            .arrange()

        val result = useCase(ServerConfig.DUMMY)

        result.test {
            assertEquals(LoginContext.EnterpriseLogin, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
        coVerify { arrangement.serverConfigRepository.observeServerConfigByLinks(eq(ServerConfig.DUMMY)) }
    }

    @Test
    fun givenLinks_whenInvokedAndError_thenBubbleUpError() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withObserveServerConfigByLinks(flowOf(NetworkFailure.ServerMiscommunication(RuntimeException()).left()))
            .arrange()

        useCase(ServerConfig.DUMMY)

        coVerify { arrangement.serverConfigRepository.observeServerConfigByLinks(eq(ServerConfig.DUMMY)) }
    }

    private class Arrangement {

        val serverConfigRepository: CustomServerConfigRepository = mock(CustomServerConfigRepository::class)

        suspend fun withObserveServerConfigByLinks(result: Flow<Either<NetworkFailure, ServerConfig>>) = apply {
            coEvery { serverConfigRepository.observeServerConfigByLinks(any()) }.returns(result)
        }

        fun arrange() = this to ObserveLoginContextUseCase(serverConfigRepository)
    }
}
