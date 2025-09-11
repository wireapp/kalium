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

package com.wire.kalium.logic.feature.sessionreset

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.message.SessionResetSender
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ResetSessionUseCaseTest {

    @Test
    fun givenProteusProviderReturningFailure_whenResettingSession_ThenReturnFailure() = runTest(TestKaliumDispatcher.io) {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withProteusTransactionResultOnly(Either.Left(failure))
            }

        val result = useCase(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        assertEquals(ResetSessionResult.Failure(failure), result)
    }

    @Test
    fun givenAnErrorWhenSendingSessionReset_whenResettingSession_ThenReturnFailure() = runTest(TestKaliumDispatcher.io) {
        val (arrangement, useCase) = Arrangement()
            .withDeleteSession()
            .withSessionResetReturning(Either.Left(failure))
            .arrange {
                withProteusTransactionReturning(Either.Right(Unit))
            }

        val result = useCase(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        coVerify { arrangement.proteusContext.deleteSession(any()) }.wasInvoked(once)
        coVerify { arrangement.sessionResetSender.invoke(any(), any(), any()) }.wasInvoked(once)
        assertEquals(ResetSessionResult.Failure(failure), result)
    }

    @Test
    fun givenMarkingDecryptionFailureAsResolvedFailed_whenResettingSession_ThenReturnFailure() = runTest(TestKaliumDispatcher.io) {
        val (arrangement, useCase) = Arrangement()
            .withSessionResetReturning(Either.Right(Unit))
            .withMarkMessagesAsDecryptionResolvedReturning(Either.Left(failure))
            .arrange {
                withProteusTransactionReturning(Either.Right(Unit))
            }

        val result = useCase(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        coVerify { arrangement.messageRepository.markMessagesAsDecryptionResolved(any(), any(), any()) }.wasInvoked(once)
        assertEquals(ResetSessionResult.Failure(failure), result)
    }

    @Test
    fun givenResetSessionCalled_whenRunningSuccessfully_thenReturnSuccessResult() = runTest(TestKaliumDispatcher.io) {
        val (arrangement, useCase) = Arrangement()
            .withSessionResetReturning(Either.Right(Unit))
            .withMarkMessagesAsDecryptionResolvedReturning(Either.Right(Unit))
            .arrange {
                withProteusTransactionReturning(Either.Right(Unit))
            }

        val result = useCase(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        assertEquals(ResetSessionResult.Success, result)
    }

    companion object {
        val CRYPTO_USER_ID = CryptoUserID("client-id", "domain")
        val failure = CoreFailure.Unknown(null)
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val sessionResetSender = mock(SessionResetSender::class)
        val messageRepository = mock(MessageRepository::class)
        val idMapper = IdMapper()

        suspend fun withSessionResetReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery { sessionResetSender.invoke(any(), any(), any()) } returns result
        }

        suspend fun withMarkMessagesAsDecryptionResolvedReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                messageRepository.markMessagesAsDecryptionResolved(any(), any(), any())
            } returns result
        }

        suspend fun withDeleteSession(): Arrangement = apply {
            coEvery { proteusContext.deleteSession(any()) } returns Unit
        }

        fun arrange(block: suspend Arrangement.() -> Unit) = let {
            runBlocking { block() }
            this to ResetSessionUseCaseImpl(
                transactionProvider = cryptoTransactionProvider,
                sessionResetSender = sessionResetSender,
                messageRepository = messageRepository,
                idMapper = idMapper,
                dispatchers = TestKaliumDispatcher
            )
        }
    }
}
