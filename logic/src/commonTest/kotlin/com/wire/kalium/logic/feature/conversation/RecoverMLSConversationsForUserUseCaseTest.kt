/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RecoverMLSConversationsForUserUseCaseTest {

    @Test
    fun givenEstablishedScanSucceeds_whenRecovering_thenJoinPendingGroupsWithExternalCommits() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withEstablishedRecovery(RecoverMLSConversationsResult.Success)
            .withPendingRecovery(Either.Right(Unit))
            .arrange()

        val result = useCase()

        assertIs<RecoverMLSConversationsForUserResult.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.recoverEstablishedMLSConversations.invoke(eq(arrangement.transactionContext))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinPendingMLSConversations.invoke(eq(true), eq(true))
        }
    }

    @Test
    fun givenEstablishedScanFails_whenRecovering_thenDoNotJoinPendingGroups() = runTest {
        val failure = StorageFailure.DataNotFound
        val (arrangement, useCase) = Arrangement()
            .withEstablishedRecovery(RecoverMLSConversationsResult.Failure(failure))
            .arrange()

        val result = useCase()

        assertEquals(RecoverMLSConversationsForUserResult.Failure(failure), result)
        verifySuspend(VerifyMode.not) {
            arrangement.joinPendingMLSConversations.invoke(any(), any())
        }
    }

    @Test
    fun givenPendingJoinFails_whenRecovering_thenReturnFailure() = runTest {
        val failure = StorageFailure.DataNotFound
        val (_, useCase) = Arrangement()
            .withEstablishedRecovery(RecoverMLSConversationsResult.Success)
            .withPendingRecovery(Either.Left(failure))
            .arrange()

        val result = useCase()

        assertEquals(RecoverMLSConversationsForUserResult.Failure(failure), result)
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val recoverEstablishedMLSConversations = mock<RecoverMLSConversationsUseCase>()
        val joinPendingMLSConversations = mock<JoinExistingMLSConversationsUseCase>()

        fun withEstablishedRecovery(result: RecoverMLSConversationsResult) = apply {
            everySuspend { recoverEstablishedMLSConversations.invoke(any()) } returns result
        }

        fun withPendingRecovery(result: Either<StorageFailure, Unit>) = apply {
            everySuspend { joinPendingMLSConversations.invoke(any(), any()) } returns result
        }

        suspend fun arrange(): Pair<Arrangement, RecoverMLSConversationsForUserUseCase> {
            withTransactionReturning(Either.Right(Unit))
            return this to RecoverMLSConversationsForUserUseCaseImpl(
                recoverEstablishedMLSConversations = recoverEstablishedMLSConversations,
                joinPendingMLSConversations = joinPendingMLSConversations,
                transactionProvider = cryptoTransactionProvider,
            )
        }
    }
}
