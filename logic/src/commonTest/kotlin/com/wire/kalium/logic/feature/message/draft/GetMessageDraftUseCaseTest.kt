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

package com.wire.kalium.logic.feature.message.draft

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.draft.MessageDraft
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetMessageDraftUseCaseTest {

    private val testDispatchers: KaliumDispatcher = TestKaliumDispatcher

    @Test
    fun givenConversationId_whenInvokingUseCase_thenShouldCallMessageDraftRepository() = runTest(testDispatchers.io) {
        val (arrangement, getMessageDraftUseCase) = Arrangement()
            .withRepositoryMessageDraftReturning(CONVERSATION_ID, null)
            .arrange()

        getMessageDraftUseCase(CONVERSATION_ID)

        verify(arrangement.messageDraftRepository)
            .coroutine { arrangement.messageDraftRepository.getMessageDraft(CONVERSATION_ID) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryReturnsNullDraft_whenInvokingUseCase_thenShouldPropagateNull() = runTest(testDispatchers.io) {
        // Given
        val (arrangement, getMessageDraftUseCase) = Arrangement()
            .withRepositoryMessageDraftReturning(CONVERSATION_ID, null)
            .arrange()

        // When
        val result = getMessageDraftUseCase(CONVERSATION_ID)

        // Then
        assertEquals(null, result)

        verify(arrangement.messageDraftRepository)
            .coroutine { arrangement.messageDraftRepository.getMessageDraft(CONVERSATION_ID) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositorySucceeds_whenInvokingUseCase_thenShouldPropagateTheDraft() = runTest(testDispatchers.io) {
        // Given
        val (arrangement, getMessageDraftUseCase) = Arrangement()
            .withRepositoryMessageDraftReturning(CONVERSATION_ID, MESSAGE_DRAFT)
            .arrange()

        // When
        val result = getMessageDraftUseCase(CONVERSATION_ID)

        // Then
        assertEquals(MESSAGE_DRAFT, result)

        verify(arrangement.messageDraftRepository)
            .coroutine { arrangement.messageDraftRepository.getMessageDraft(CONVERSATION_ID) }
            .wasInvoked(exactly = once)
    }

    private inner class Arrangement {

        @Mock
        val messageDraftRepository: MessageDraftRepository = mock(MessageDraftRepository::class)

        private val getMessageDraft by lazy {
            GetMessageDraftUseCaseImpl(messageDraftRepository, testDispatchers)
        }

        suspend fun withRepositoryMessageDraftReturning(
            conversationId: ConversationId,
            response: MessageDraft?
        ) = apply {
            given(messageDraftRepository)
                .coroutine { messageDraftRepository.getMessageDraft(conversationId) }
                .thenReturn(response)
        }

        fun arrange() = this to getMessageDraft
    }

    private companion object {
        val CONVERSATION_ID = TestConversation.ID
        val MESSAGE_DRAFT = MessageDraft(
            conversationId = CONVERSATION_ID,
            text = "hello",
            editMessageId = null,
            quotedMessageId = null,
            selectedMentionList = listOf()
        )
    }
}
