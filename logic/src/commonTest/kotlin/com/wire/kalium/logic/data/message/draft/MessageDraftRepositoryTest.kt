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

package com.wire.kalium.logic.data.message.draft

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.persistence.dao.message.draft.MessageDraftDAO
import com.wire.kalium.persistence.dao.message.draft.MessageDraftEntity
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageDraftRepositoryTest {

    @Test
    fun givenAConversationId_whenGettingDraftMessage_thenShouldReturnMessageDraft() = runTest {
        // Given
        val (arrangement, messageDraftRepository) = Arrangement()
            .withGetMessageDraft(TEST_MESSAGE_DRAFT_ENTITY)
            .arrange()

        // When
        val result = messageDraftRepository.getMessageDraft(TEST_CONVERSATION_ID)

        // Then
        assertEquals(TEST_MESSAGE_DRAFT_ENTITY.toModel(), result)

        with(arrangement) {
            coVerify {
                messageDraftDAO.getMessageDraft(eq(TEST_CONVERSATION_ID.toDao()))
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenADraft_whenSavingDraftMessage_thenShouldMapItProperly() = runTest {
        // Given
        val (arrangement, messageDraftRepository) = Arrangement()
            .withUpsertMessageDraft()
            .arrange()

        // When
        messageDraftRepository.saveMessageDraft(TEST_MESSAGE_DRAFT)

        // Then
        with(arrangement) {
            coVerify {
                messageDraftDAO.upsertMessageDraft(TEST_MESSAGE_DRAFT.toDao())
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAConversationId_whenRemovingMessageDraft_thenShouldReturnSuccess() = runTest {
        // Given
        val (arrangement, messageDraftRepository) = Arrangement()
            .withRemoveMessageDraftSucceeding()
            .arrange()

        // When
        messageDraftRepository.removeMessageDraft(TEST_CONVERSATION_ID)

        // Then
        with(arrangement) {
            coVerify {
                messageDraftDAO.removeMessageDraft(eq(TEST_CONVERSATION_ID.toDao()))
            }.wasInvoked(exactly = once)
        }
    }

    private class Arrangement {
        val messageDraftDAO = mock(MessageDraftDAO::class)

        suspend fun withRemoveMessageDraftSucceeding() = apply {
            coEvery {
                messageDraftDAO.removeMessageDraft(any())
            }.returns(Unit)
        }

        suspend fun withGetMessageDraft(result: MessageDraftEntity?) = apply {
            coEvery {
                messageDraftDAO.getMessageDraft(any())
            }.returns(result)
        }

        suspend fun withUpsertMessageDraft() = apply {
            coEvery {
                messageDraftDAO.upsertMessageDraft(any())
            }.returns(Unit)
        }

        fun arrange() = this to MessageDraftDataSource(
            messageDraftDAO = messageDraftDAO
        )
    }

    private companion object {
        val TEST_CONVERSATION_ID = ConversationId("value", "domain")
        val TEST_MESSAGE_DRAFT =
            MessageDraft(
                conversationId = TEST_CONVERSATION_ID,
                text = "hello",
                editMessageId = null,
                quotedMessageId = null,
                selectedMentionList = listOf()
            )
        val TEST_MESSAGE_DRAFT_ENTITY =
            MessageDraftEntity(
                conversationId = TEST_CONVERSATION_ID.toDao(),
                text = "hello",
                editMessageId = null,
                quotedMessageId = null,
                selectedMentionList = listOf()
            )
    }
}
