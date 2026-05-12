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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.FetchConversationsUseCase
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SyncConversationsUseCaseTest {

    @Test
    fun givenUseCase_whenInvoked_thenFetchConversations() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withGetConversationsIdsReturning(emptyList())
            .withFetchConversationsSuccessful()
            .arrange()

        useCase.invoke()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.fetchConversations(any())
        }
    }

    @Test
    fun givenProtocolChanges_whenInvoked_thenInsertHistoryLostSystemMessage() = runTest {
        val conversationId = TestConversation.ID
        val (arrangement, useCase) = Arrangement()
            .withGetConversationsIdsReturning(listOf(conversationId), protocol = Conversation.Protocol.PROTEUS)
            .withFetchConversationsSuccessful()
            .withGetConversationsIdsReturning(listOf(conversationId), protocol = Conversation.Protocol.MLS)
            .withInsertHistoryLostProtocolChangedSystemMessageSuccessful()
            .arrange()

        useCase.invoke()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.systemMessageInserter.insertHistoryLostProtocolChangedSystemMessage(conversationId)
        }
    }

    @Test
    fun givenProtocolIsUnchanged_whenInvoked_thenDoNotInsertHistoryLostSystemMessage() = runTest {
        val conversationId = TestConversation.ID
        val (arrangement, useCase) = Arrangement()
            .withGetConversationsIdsReturning(listOf(conversationId), protocol = Conversation.Protocol.PROTEUS)
            .withFetchConversationsSuccessful()
            .withGetConversationsIdsReturning(emptyList(), protocol = Conversation.Protocol.MLS)
            .withInsertHistoryLostProtocolChangedSystemMessageSuccessful()
            .arrange()

        useCase.invoke()

        verifySuspend(VerifyMode.not) {
            arrangement.systemMessageInserter.insertHistoryLostProtocolChangedSystemMessage(conversationId)
        }
    }

    private class Arrangement: CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val systemMessageInserter = mock<SystemMessageInserter>(mode = MockMode.autoUnit)
        val fetchConversations = mock<FetchConversationsUseCase>(mode = MockMode.autoUnit)

        suspend fun withFetchConversationsSuccessful() = apply {
            everySuspend {
                fetchConversations(any())
            } returns Either.Right(Unit)
        }

        suspend fun withGetConversationsIdsReturning(
            conversationIds: List<ConversationId>,
            protocol: Conversation.Protocol? = null
        ) = apply {
            everySuspend {
                conversationRepository.getConversationIds(any(), any(), any())
            } returns Either.Right(conversationIds)
        }

        suspend fun withInsertHistoryLostProtocolChangedSystemMessageSuccessful() = apply {
            everySuspend { systemMessageInserter.insertHistoryLostProtocolChangedSystemMessage(any()) } returns Unit
        }

        suspend fun arrange() = this to SyncConversationsUseCaseImpl(
            conversationRepository,
            systemMessageInserter,
            fetchConversations,
            cryptoTransactionProvider
        ).also {
            withTransactionReturning(Either.Right(Unit))
        }
    }
}
