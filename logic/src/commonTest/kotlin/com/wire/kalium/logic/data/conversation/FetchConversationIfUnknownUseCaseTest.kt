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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class FetchConversationIfUnknownUseCaseTest {

    @Test
    fun whenConversationDoesNotExist_shouldFetchIt() = runTest {
        val (arrangement, useCase) = arrange {
            withGetConversationLeft()
            withFetchConversationSuccess()
        }

        useCase(arrangement.transactionContext, TestConversation.ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.fetchConversation(
                any(),
                eq(TestConversation.ID),
                eq(ConversationSyncReason.Other)
            )
        }
    }

    @Test
    fun whenConversationExists_shouldNotFetchIt() = runTest {
        val (arrangement, useCase) = arrange {
            withGetConversationRight()
        }

        useCase(arrangement.transactionContext, TestConversation.ID)

        verifySuspend(VerifyMode.not) { arrangement.fetchConversation(any(), any(), any()) }
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, FetchConversationIfUnknownUseCase> =
        Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {

        val conversationRepository = mock<ConversationRepository>()
        val fetchConversation = mock<FetchConversationUseCase>()

        suspend fun withGetConversationLeft() = apply {
            everySuspend {
                conversationRepository.getConversationById(eq(TestConversation.ID))
            } returns Either.Left(StorageFailure.DataNotFound)
        }

        suspend fun withGetConversationRight() = apply {
            everySuspend {
                conversationRepository.getConversationById(eq(TestConversation.ID))
            } returns Either.Right(TestConversation.CONVERSATION)
        }

        suspend fun withFetchConversationSuccess() = apply {
            everySuspend {
                fetchConversation(
                    any(),
                    eq(TestConversation.ID),
                    eq(ConversationSyncReason.Other)
                )
            } returns Either.Right(Unit)
        }

        fun arrange(): Pair<Arrangement, FetchConversationIfUnknownUseCase> {
            runBlocking { block() }
            return this to FetchConversationIfUnknownUseCaseImpl(
                conversationRepository = conversationRepository,
                fetchConversation = fetchConversation
            )
        }
    }
}
