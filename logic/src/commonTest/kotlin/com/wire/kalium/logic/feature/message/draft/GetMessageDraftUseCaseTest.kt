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
package com.wire.kalium.logic.feature.message.draft

import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.draft.MessageDraft
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetMessageDraftUseCaseTest {

    private val testDispatchers: KaliumDispatcher = TestKaliumDispatcher

    @Test
    fun givenNoDraft_whenUseCaseInvoked_thenNullReturned() = runTest(testDispatchers.io) {
        val (_, useCase) = Arrangement()
            .withNoDraft()
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertNull(result)
    }

    @Test
    fun givenDraftIsNotEdit_whenUseCaseInvoked_thenMessageIsNotFetched() = runTest(testDispatchers.io) {
        val (arrangement, useCase) = Arrangement()
            .withDraft(MessageDraft(
                conversationId = CONVERSATION_ID,
                text = "text",
                editMessageId = null,
                quotedMessageId = null,
                selectedMentionList = emptyList()
            ))
            .arrange()

        useCase(CONVERSATION_ID)

        coVerify {
            arrangement.messageRepository.getMessageById(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenDraftIsEditRegular_whenUseCaseInvoked_thenCorrectValueReturned() = runTest(testDispatchers.io) {
        val (_, useCase) = Arrangement()
            .withRegularMessageEdit()
            .withDraft(MessageDraft(
                conversationId = CONVERSATION_ID,
                text = "text",
                editMessageId = "message_id",
                quotedMessageId = null,
                selectedMentionList = emptyList()
            ))
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertTrue(result?.isMultipartEdit == false)
    }

    @Test
    fun givenDraftIsEditMultipart_whenUseCaseInvoked_thenCorrectValueReturned() = runTest(testDispatchers.io) {
        val (_, useCase) = Arrangement()
            .withMultipartMessageEdit()
            .withDraft(MessageDraft(
                conversationId = CONVERSATION_ID,
                text = "text",
                editMessageId = "message_id",
                quotedMessageId = null,
                selectedMentionList = emptyList()
            ))
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertTrue(result?.isMultipartEdit == true)
    }

    private inner class Arrangement {

        val messageRepository: MessageRepository = mock(MessageRepository::class)
        val messageDraftRepository: MessageDraftRepository = mock(MessageDraftRepository::class)

        suspend fun withNoDraft() = apply {
            coEvery {
                messageDraftRepository.getMessageDraft(any())
            } returns null
        }

        suspend fun withDraft(draft: MessageDraft) = apply {
            coEvery {
                messageDraftRepository.getMessageDraft(any())
            } returns draft
        }

        suspend fun withRegularMessageEdit() = apply {
            coEvery {
                messageRepository.getMessageById(any(), any())
            } returns TestMessage.TEXT_MESSAGE.right()
        }

        suspend fun withMultipartMessageEdit() = apply {
            coEvery {
                messageRepository.getMessageById(any(), any())
            } returns TestMessage.multipartMessage(emptyList()).right()
        }

        fun arrange() = this to GetMessageDraftUseCaseImpl(
            messageRepository = messageRepository,
            messageDraftRepository = messageDraftRepository,
            dispatcher = testDispatchers
        )
    }

    private companion object {
        val CONVERSATION_ID = QualifiedID("conversation_id", "domain")
    }
}
