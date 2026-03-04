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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.model.Conversation
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.ConversationDetails
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetCellGroupConversationsUseCaseTest {

    private companion object {
        private const val CONVERSATION_ID_1 = "conv1@wire.com"
        private const val CONVERSATION_ID_2 = "conv2@wire.com"
        private const val CONVERSATION_NAME_1 = "Engineering"
        private const val CONVERSATION_NAME_2 = "Design"
    }

    @Test
    fun given_GroupConversationsWithWireCell_whenInvoked_thenReturnConversations() = runTest {
        val (_, useCase) = Arrangement()
            .withConversationDetails(
                listOf(
                    Conversation(CONVERSATION_ID_1, CONVERSATION_NAME_1, isChannel = false, channelAccess = null),
                    Conversation(CONVERSATION_ID_2, CONVERSATION_NAME_2, isChannel = true, channelAccess = ConversationDetails.Group.Channel.ChannelAccess.PUBLIC)
                )
            )
            .arrange()

        val result = useCase()

        assertIs<GetConversationsUseCaseResult.Success>(result)
        assertEquals(2, result.conversations.size)
        assertEquals(CONVERSATION_NAME_1, result.conversations[0].name)
        assertEquals(CONVERSATION_NAME_2, result.conversations[1].name)
        assertEquals(CONVERSATION_ID_1, result.conversations[0].id)
        assertEquals(CONVERSATION_ID_2, result.conversations[1].id)
        assertEquals(false, result.conversations[0].isChannel)
        assertEquals(true, result.conversations[1].isChannel)
        assertEquals(null, result.conversations[0].channelAccess)
        assertEquals(ConversationDetails.Group.Channel.ChannelAccess.PUBLIC, result.conversations[1].channelAccess)
    }

    @Test
    fun given_NonGroupConversations_whenInvoked_thenFilterThemOut() = runTest {
        val (_, useCase) = Arrangement()
            .withConversationDetails(
                listOf(
                    Conversation(CONVERSATION_ID_1, CONVERSATION_NAME_1, isChannel = false, channelAccess = null)
                )
            )
            .arrange()

        val result = useCase()

        assertIs<GetConversationsUseCaseResult.Success>(result)
        assertEquals(1, result.conversations.size)
    }

    @Test
    fun given_EmptyConversations_whenInvoked_thenReturnEmptyList() = runTest {
        val (_, useCase) = Arrangement()
            .withConversationDetails(emptyList())
            .arrange()

        val result = useCase()

        assertIs<GetConversationsUseCaseResult.Success>(result)
        assertEquals(0, result.conversations.size)
    }

    @Test
    fun given_RepositoryFails_whenInvoked_thenReturnFailure() = runTest {
        val storageFailure: StorageFailure = StorageFailure.DataNotFound
        val (_, useCase) = Arrangement()
            .withConversationDetailsError(storageFailure)
            .arrange()

        val result = useCase()

        assertIs<GetConversationsUseCaseResult.Failure>(result)
        assertEquals(storageFailure, result.failure)
    }

    @Test
    fun given_PrivateChannel_whenInvoked_thenReturnConversationWithPrivateAccess() = runTest {
        val (_, useCase) = Arrangement()
            .withConversationDetails(
                listOf(
                    Conversation(CONVERSATION_ID_1, CONVERSATION_NAME_1, isChannel = true, channelAccess = ConversationDetails.Group.Channel.ChannelAccess.PRIVATE)
                )
            )
            .arrange()

        val result = useCase()

        assertIs<GetConversationsUseCaseResult.Success>(result)
        assertEquals(1, result.conversations.size)
        assertEquals(true, result.conversations[0].isChannel)
        assertEquals(ConversationDetails.Group.Channel.ChannelAccess.PRIVATE, result.conversations[0].channelAccess)
    }

    @Test
    fun given_RegularGroup_whenInvoked_thenReturnConversationWithoutChannelInfo() = runTest {
        val (_, useCase) = Arrangement()
            .withConversationDetails(
                listOf(
                    Conversation(CONVERSATION_ID_1, CONVERSATION_NAME_1, isChannel = false, channelAccess = null)
                )
            )
            .arrange()

        val result = useCase()

        assertIs<GetConversationsUseCaseResult.Success>(result)
        assertEquals(1, result.conversations.size)
        assertEquals(false, result.conversations[0].isChannel)
        assertEquals(null, result.conversations[0].channelAccess)
    }

    private class Arrangement {
        private val conversationRepository = mock(CellConversationRepository::class)

        private var conversationDetailsResult: Either<StorageFailure, List<Conversation>> = listOf<Conversation>().right()

        fun withConversationDetails(details: List<Conversation>) = apply {
            conversationDetailsResult = details.right()
        }

        fun withConversationDetailsError(failure: StorageFailure) = apply {
            conversationDetailsResult = failure.left()
        }

        suspend fun arrange(): Pair<Arrangement, GetCellGroupConversationsUseCase> {
            coEvery {
                conversationRepository.getCellGroupConversations()
            }.returns(conversationDetailsResult)

            return this to GetCellGroupConversationsUseCaseImpl(conversationRepository)
        }
    }
}

