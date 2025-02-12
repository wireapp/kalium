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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SelfServerConfigUseCaseTest {

    @Test
    fun givenUserSession_whenGetSelfServerConfig_thenReturnSelfServerConfig() = runTest {
        val expected = TEST_SERVER_CONFIG
        val (arrangement, selfServerConfigUseCase) = Arrangement()
            .withServerConfigSuccessResponse(selfUserId, expected)
            .arrange()

        selfServerConfigUseCase().also { result ->
            assertIs<SelfServerConfigUseCase.Result.Success>(result)
            assertEquals(expected, result.serverLinks)
        }

        coVerify {
            arrangement.serverConfigRepository.configForUser(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenError_whenGetSelfServerConfig_thenReturnError() = runTest {
        val (arrangement, selfServerConfigUseCase) = Arrangement()
            .withServerConfigErrorResponse(selfUserId, StorageFailure.DataNotFound)
            .arrange()

        selfServerConfigUseCase().also { result ->
            assertIs<SelfServerConfigUseCase.Result.Failure>(result)
            assertEquals(StorageFailure.DataNotFound, result.cause)
        }

        coVerify {
            arrangement.serverConfigRepository.configForUser(any())
        }.wasInvoked(exactly = once)
    }

    private companion object {
        private val TEST_SERVER_CONFIG: ServerConfig = newServerConfig(1)

        val selfUserId = UserId("self_id", "self_domain")
    }

    private class Arrangement {

        @Mock
        val serverConfigRepository = mock(ServerConfigRepository::class)

        val selfServerConfigUseCase = SelfServerConfigUseCase(selfUserId, serverConfigRepository)
        suspend fun withServerConfigSuccessResponse(userId: UserId, serverConfig: ServerConfig): Arrangement = apply {
            coEvery {
                serverConfigRepository.configForUser(userId)
            }.returns(Either.Right(TEST_SERVER_CONFIG))
        }

        suspend fun withServerConfigErrorResponse(userId: UserId, storageFailure: StorageFailure): Arrangement = apply {
            coEvery {
                serverConfigRepository.configForUser(userId)
            }.returns(Either.Left(storageFailure))
        }

        fun arrange() = this to selfServerConfigUseCase
    }
}
