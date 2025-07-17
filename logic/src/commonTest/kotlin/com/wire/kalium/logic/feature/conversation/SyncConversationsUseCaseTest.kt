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
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.doesNothing
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
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

        coVerify {
            arrangement.fetchConversations()
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.systemMessageInserter.insertHistoryLostProtocolChangedSystemMessage(eq(conversationId))
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.systemMessageInserter.insertHistoryLostProtocolChangedSystemMessage(eq(conversationId))
        }.wasNotInvoked()
    }

    private class Arrangement {

        val conversationRepository = mock(ConversationRepository::class)
        val systemMessageInserter = mock(SystemMessageInserter::class)
        val fetchConversations = mock(FetchConversationsUseCase::class)

        suspend fun withFetchConversationsSuccessful() = apply {
            coEvery {
                fetchConversations()
            }.returns(Either.Right(Unit))
        }

        suspend fun withGetConversationsIdsReturning(
            conversationIds: List<ConversationId>,
            protocol: Conversation.Protocol? = null
        ) = apply {
            coEvery {
                conversationRepository.getConversationIds(eq(Conversation.Type.Group.Regular), protocol?.let { eq(it) } ?: any(), eq<TeamId?>(null))
            }.returns(Either.Right(conversationIds))
        }

        suspend fun withInsertHistoryLostProtocolChangedSystemMessageSuccessful() = apply {
            coEvery { systemMessageInserter.insertHistoryLostProtocolChangedSystemMessage(any()) }
                .doesNothing()
        }

        fun arrange() = this to SyncConversationsUseCaseImpl(
            conversationRepository,
            systemMessageInserter,
            fetchConversations
        )
    }
}
