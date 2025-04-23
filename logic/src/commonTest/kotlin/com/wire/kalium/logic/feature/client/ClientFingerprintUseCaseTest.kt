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
package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.prekey.UsersWithoutSessions
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ClientFingerprintUseCaseTest {

    @Test
    fun givenClientHaveSession_thenReturnFingerprint() = runTest {
        val fingerprint = byteArrayOf(1, 2, 3, 4, 5)

        val userId = TestUser.USER_ID
        val clientId = TestClient.CLIENT_ID

        val (arrange, userCase) = Arrangement()
            .withRemoteFingerprintSuccess(fingerprint)
            .arrange()

        userCase(userId, clientId).also { result ->
            assertIs<Result.Success>(result)
            assertEquals(fingerprint, result.fingerprint)
        }

        coVerify {
            arrange.proteusClient.remoteFingerPrint(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrange.preKeyRepository.establishSessions(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenClientHaveNoSession_thenEstablishANewSession() = runTest {
        val fingerprint = byteArrayOf(1, 2, 3, 4, 5)

        val userId = TestUser.USER_ID
        val clientId = TestClient.CLIENT_ID

        val (arrange, userCase) = Arrangement()
            .withSessionNotFound(fingerprint)
            .withEstablishSession(Either.Right(UsersWithoutSessions.EMPTY))
            .arrange()

        userCase(userId, clientId).also { result ->
            assertIs<Result.Success>(result)
            assertEquals(fingerprint, result.fingerprint)
        }

        coVerify {
            arrange.proteusClient.remoteFingerPrint(any())
        }.wasInvoked(exactly = twice)

        coVerify {
            arrange.preKeyRepository.establishSessions(eq(mapOf(userId to listOf(clientId))))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusException_whenGettingRemoteFingerPrint_thenErrorIsReturned() = runTest {
        val error = ProteusException(null, ProteusException.Code.DECODE_ERROR, 3)

        val userId = TestUser.USER_ID
        val clientId = TestClient.CLIENT_ID

        val (arrange, userCase) = Arrangement()
            .withRemoteFingerprintFailure(error)
            .arrange()

        userCase(userId, clientId).also { result ->
            assertIs<Result.Failure>(result)
            assertIs<ProteusFailure>(result.error)
            assertEquals(error.code, (result.error as ProteusFailure).proteusException.code)
        }

        coVerify {
            arrange.proteusClient.remoteFingerPrint(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrange.preKeyRepository.establishSessions(any())
        }.wasNotInvoked()
    }

    private class Arrangement {

        val preKeyRepository = mock(PreKeyRepository::class)
        private val proteusClientProvider = mock(ProteusClientProvider::class)
        val proteusClient = mock(ProteusClient::class)

        val userCase = ClientFingerprintUseCaseImpl(
            proteusClientProvider = proteusClientProvider,
            prekeyRepository = preKeyRepository
        )

        suspend fun withRemoteFingerprintFailure(error: ProteusException) = apply {
            coEvery {
                proteusClient.remoteFingerPrint(any())
            }.throws(error)
        }

        private var getSessionCalled = 0
        suspend fun withSessionNotFound(secondTimeResult: ByteArray) = apply {
            coEvery { proteusClient.remoteFingerPrint(any()) }
                .invokes { _ ->
                    if (getSessionCalled == 0) {
                        getSessionCalled++
                        throw ProteusException(null, ProteusException.Code.SESSION_NOT_FOUND, 2)
                    }
                    secondTimeResult
                }
        }

        suspend fun withRemoteFingerprintSuccess(result: ByteArray) = apply {
            coEvery {
                proteusClient.remoteFingerPrint(any())
            }.returns(result)
        }

        suspend fun withEstablishSession(result: Either<CoreFailure, UsersWithoutSessions>) = apply {
            coEvery {
                preKeyRepository.establishSessions(any())
            }.returns(result)
        }

        suspend fun arrange() = this to userCase.also {
            coEvery {
                proteusClientProvider.getOrError()
            }.returns(Either.Right(proteusClient))
        }
    }
}
