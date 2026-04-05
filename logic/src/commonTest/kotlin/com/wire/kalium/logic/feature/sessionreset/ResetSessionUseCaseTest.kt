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
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.matcher.any as mokkeryAny
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusContext.deleteSession(mokkeryAny())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sessionResetSender.invoke(mokkeryAny(), mokkeryAny(), mokkeryAny())
        }
        assertEquals(ResetSessionResult.Failure(failure), result)
    }

    @Test
    fun givenMarkingDecryptionFailureAsResolvedFailed_whenResettingSession_ThenReturnFailure() = runTest(TestKaliumDispatcher.io) {
        val (arrangement, useCase) = Arrangement()
            .withSessionResetReturning(Either.Right(Unit))
            .withMarkProteusMessagesAsDecryptionResolvedReturning(Either.Left(failure))
            .arrange {
                withProteusTransactionReturning(Either.Right(Unit))
            }

        val result = useCase(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.markProteusMessagesAsDecryptionResolved(mokkeryAny(), mokkeryAny())
        }
        assertEquals(ResetSessionResult.Failure(failure), result)
    }

    @Test
    fun givenResetSessionCalled_whenRunningSuccessfully_thenReturnSuccessResult() = runTest(TestKaliumDispatcher.io) {
        val (arrangement, useCase) = Arrangement()
            .withSessionResetReturning(Either.Right(Unit))
            .withMarkProteusMessagesAsDecryptionResolvedReturning(Either.Right(Unit))
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
        val sessionResetSender = mock<SessionResetSender>()
        val messageRepository = mock<MessageRepository>()
        val idMapper = IdMapper()

        suspend fun withSessionResetReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { sessionResetSender.invoke(mokkeryAny(), mokkeryAny(), mokkeryAny()) } returns result
        }

        suspend fun withMarkProteusMessagesAsDecryptionResolvedReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                messageRepository.markProteusMessagesAsDecryptionResolved(mokkeryAny(), mokkeryAny())
            } returns result
        }

        suspend fun withDeleteSession(): Arrangement = apply {
            everySuspend { proteusContext.deleteSession(mokkeryAny()) } returns Unit
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
