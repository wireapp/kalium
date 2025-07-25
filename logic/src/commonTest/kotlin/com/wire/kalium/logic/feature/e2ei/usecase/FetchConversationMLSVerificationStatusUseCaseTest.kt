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
package com.wire.kalium.logic.feature.e2ei.usecase

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.FetchMLSVerificationStatusArrangement
import com.wire.kalium.logic.util.arrangement.usecase.FetchMLSVerificationStatusArrangementImpl
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FetchConversationMLSVerificationStatusUseCaseTest {

    @Test
    fun givenErrorOnGettingConversation_whenCalledToCheck_thenFetchingMLSVerificationIsNotCalled() = runTest {
        val (arrangement, useCase) = arrange {
            withConversationDetailsByIdReturning(Either.Left(StorageFailure.DataNotFound))
        }

        useCase(TestConversation.ID)
        advanceUntilIdle()

        coVerify { arrangement.fetchMLSVerificationStatusUseCase(any(), any()) }
            .wasNotInvoked()
    }

    @Test
    fun givenProteusConversation_whenCalledToCheck_thenFetchingMLSVerificationIsNotCalled() = runTest {
        val (arrangement, useCase) = arrange {
            withConversationDetailsByIdReturning(Either.Right(TestConversation.ONE_ON_ONE()))
        }

        useCase(TestConversation.ID)
        advanceUntilIdle()

        coVerify { arrangement.fetchMLSVerificationStatusUseCase(any(), any()) }
            .wasNotInvoked()
    }

    @Test
    fun givenMLSConversation_whenCalledToCheck_thenFetchingMLSVerificationIsCalled() = runTest {
        val protocolInfo = TestConversation.MLS_PROTOCOL_INFO
        val (arrangement, useCase) = arrange {
            withConversationDetailsByIdReturning(Either.Right(TestConversation.ONE_ON_ONE(protocolInfo)))
        }

        useCase(TestConversation.ID)
        advanceUntilIdle()

        coVerify { arrangement.fetchMLSVerificationStatusUseCase(any(), eq(protocolInfo.groupId)) }
            .wasInvoked()
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) : FetchMLSVerificationStatusArrangement by FetchMLSVerificationStatusArrangementImpl(),
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl() {

        suspend fun arrange() = let {
            block()
            withMLSTransactionReturning(Either.Right(Unit))
            mockFetchMLSVerificationStatus()
            this to FetchConversationMLSVerificationStatusUseCaseImpl(
                conversationRepository = conversationRepository,
                fetchMLSVerificationStatusUseCase = fetchMLSVerificationStatusUseCase,
                transactionProvider = cryptoTransactionProvider
            )
        }
    }
}
