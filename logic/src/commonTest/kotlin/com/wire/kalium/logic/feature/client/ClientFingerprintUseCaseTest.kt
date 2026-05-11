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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.prekey.UsersWithoutSessions
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ClientFingerprintUseCaseTest {

    @Test
    fun givenClientHaveSession_thenReturnFingerprint() = runTest {
        val fingerprint = "fingerprint"

        val userId = TestUser.USER_ID
        val clientId = TestClient.CLIENT_ID

        val (arrange, userCase) = Arrangement()
            .withRemoteFingerprintSuccess(fingerprint)
            .arrange {
                withProteusTransactionReturning(Either.Right(Unit))
            }

        userCase(userId, clientId).also { result ->
            assertIs<Result.Success>(result)
            assertEquals(fingerprint, result.fingerprint)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrange.proteusContext.remoteFingerPrint(any())
        }

        verifySuspend(VerifyMode.not) {
            arrange.preKeyRepository.establishSessions(any(), any())
        }
    }

    @Test
    fun givenClientHaveNoSession_thenEstablishANewSession() = runTest {
        val fingerprint = "fingerprint"

        val userId = TestUser.USER_ID
        val clientId = TestClient.CLIENT_ID

        val (arrange, userCase) = Arrangement()
            .withSessionNotFound(fingerprint)
            .withEstablishSession(Either.Right(UsersWithoutSessions.EMPTY))
            .arrange {
                withProteusTransactionReturning(Either.Right(Unit))
            }

        userCase(userId, clientId).also { result ->
            assertIs<Result.Success>(result)
            assertEquals(fingerprint, result.fingerprint)
        }

        verifySuspend(VerifyMode.exactly(2)) {
            arrange.proteusContext.remoteFingerPrint(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrange.preKeyRepository.establishSessions(any(), eq(mapOf(userId to listOf(clientId))))
        }
    }

    @Test
    fun givenProteusException_whenGettingRemoteFingerPrint_thenErrorIsReturned() = runTest {
        val error = ProteusException(null, ProteusException.Code.DECODE_ERROR, 3)

        val userId = TestUser.USER_ID
        val clientId = TestClient.CLIENT_ID

        val (arrange, userCase) = Arrangement()
            .withRemoteFingerprintFailure(error)
            .arrange {
                withProteusTransactionReturning(Either.Right(Unit))
            }

        userCase(userId, clientId).also { result ->
            assertIs<Result.Failure>(result)
            assertIs<ProteusFailure>(result.error)
            assertEquals(error.code, (result.error as ProteusFailure).proteusException.code)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrange.proteusContext.remoteFingerPrint(any())
        }

        verifySuspend(VerifyMode.not) {
            arrange.preKeyRepository.establishSessions(any(), any())
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val preKeyRepository = mock<PreKeyRepository>(mode = MockMode.autoUnit)

        val userCase = ClientFingerprintUseCaseImpl(
            prekeyRepository = preKeyRepository,
            transactionProvider = cryptoTransactionProvider
        )

        suspend fun withRemoteFingerprintFailure(error: ProteusException) = apply {
            everySuspend {
                proteusContext.remoteFingerPrint(any())
            } throws error
        }

        private var getSessionCalled = 0
        suspend fun withSessionNotFound(secondTimeResult: String) = apply {
            everySuspend { proteusContext.remoteFingerPrint(any()) } calls {
                if (getSessionCalled == 0) {
                    getSessionCalled++
                    throw ProteusException(null, ProteusException.Code.SESSION_NOT_FOUND, 2)
                }
                secondTimeResult
            }
        }

        suspend fun withRemoteFingerprintSuccess(result: String) = apply {
            everySuspend {
                proteusContext.remoteFingerPrint(any())
            } returns result
        }

        suspend fun withEstablishSession(result: Either<CoreFailure, UsersWithoutSessions>) = apply {
            everySuspend {
                preKeyRepository.establishSessions(any(), any())
            } returns result
        }

        fun arrange(block: suspend Arrangement.() -> Unit) = let {
            runBlocking { block() }
            this to userCase
        }
    }
}
