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
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.prekey.UsersWithoutSessions
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
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

        verify(arrange.proteusClient)
            .suspendFunction(arrange.proteusClient::remoteFingerPrint)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrange.preKeyRepository)
            .suspendFunction(arrange.preKeyRepository::establishSessions)
            .with(any())
            .wasNotInvoked()
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

        verify(arrange.proteusClient)
            .suspendFunction(arrange.proteusClient::remoteFingerPrint)
            .with(any())
            .wasInvoked(exactly = twice)


        verify(arrange.preKeyRepository)
            .suspendFunction(arrange.preKeyRepository::establishSessions)
            .with(eq(mapOf(userId to listOf(clientId))))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusException_whenGettingRemoteFingerPrint_thenErrorIsReturned() = runTest {
        val error = ProteusException(null, ProteusException.Code.DECODE_ERROR)

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

        verify(arrange.proteusClient)
            .suspendFunction(arrange.proteusClient::remoteFingerPrint)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrange.preKeyRepository)
            .suspendFunction(arrange.preKeyRepository::establishSessions)
            .with(any())
            .wasNotInvoked()
    }


    private class Arrangement {

        @Mock
        val preKeyRepository = mock(PreKeyRepository::class)

        @Mock
        private val proteusClientProvider = mock(ProteusClientProvider::class)

        @Mock
        val proteusClient = mock(ProteusClient::class)

        val userCase = ClientFingerprintUseCaseImpl(
            proteusClientProvider = proteusClientProvider,
            prekeyRepository = preKeyRepository
        )


        init {
            given(proteusClientProvider)
                .suspendFunction(proteusClientProvider::getOrError)
                .whenInvoked()
                .thenReturn(Either.Right(proteusClient))
        }

        fun withRemoteFingerprintFailure(error: ProteusException) = apply {
            given(proteusClient)
                .suspendFunction(proteusClient::remoteFingerPrint)
                .whenInvokedWith(any())
                .thenThrow(error)
        }

        private var getSessionCalled = 0
        fun withSessionNotFound(secondTimeResult: ByteArray) = apply {
            given(proteusClient)
                .suspendFunction(proteusClient::remoteFingerPrint)
                .whenInvokedWith(any())
                .then {
                    if (getSessionCalled == 0) {
                        getSessionCalled++
                        throw ProteusException(null, ProteusException.Code.SESSION_NOT_FOUND)
                    }
                    secondTimeResult
                }
        }

        fun withRemoteFingerprintSuccess(result: ByteArray) = apply {
            given(proteusClient)
                .suspendFunction(proteusClient::remoteFingerPrint)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withEstablishSession(result: Either<CoreFailure, UsersWithoutSessions>) = apply {
            given(preKeyRepository)
                .suspendFunction(preKeyRepository::establishSessions)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun arrange() = this to userCase
    }
}
