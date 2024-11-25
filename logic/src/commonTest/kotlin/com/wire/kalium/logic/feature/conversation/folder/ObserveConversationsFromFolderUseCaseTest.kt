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
package com.wire.kalium.logic.feature.conversation.folder

import com.wire.kalium.logic.data.conversation.ConversationDetailsWithEvents
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.framework.TestConversationDetails
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveConversationsFromFolderUseCaseTest {

    @Test
    fun givenFolderId_WhenConversationsExist_ThenReturnFlowWithConversations() = runTest {
        val testFolderId = "test-folder-id"
        val testConversations = listOf(
            TestConversationDetails.CONNECTION,
            TestConversationDetails.CONVERSATION_ONE_ONE,
        ).map {
            ConversationDetailsWithEvents(
                conversationDetails = it
            )
        }

        val (arrangement, observeConversationsUseCase) = Arrangement()
            .withConversationsFromFolder(testFolderId, testConversations)
            .arrange()

        val result = observeConversationsUseCase(testFolderId).first()

        assertEquals(testConversations, result)

        coVerify {
            arrangement.conversationFolderRepository.observeConversationsFromFolder(testFolderId)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenFolderId_WhenNoConversationsExist_ThenReturnEmptyFlow() = runTest {
        val testFolderId = "test-folder-id"

        val (arrangement, observeConversationsUseCase) = Arrangement()
            .withConversationsFromFolder(testFolderId, emptyList())
            .arrange()

        val result = observeConversationsUseCase(testFolderId).first()

        assertEquals(emptyList<ConversationDetailsWithEvents>(), result)

        coVerify {
            arrangement.conversationFolderRepository.observeConversationsFromFolder(testFolderId)
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val conversationFolderRepository = mock(ConversationFolderRepository::class)

        private val observeConversationsFromFolderUseCase = ObserveConversationsFromFolderUseCaseImpl(
            conversationFolderRepository
        )

        suspend fun withConversationsFromFolder(folderId: String, conversationList: List<ConversationDetailsWithEvents>) = apply {
            coEvery {
                conversationFolderRepository.observeConversationsFromFolder(folderId)
            }.returns(flowOf(conversationList))
        }

        fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).let { this to observeConversationsFromFolderUseCase }
    }
}
