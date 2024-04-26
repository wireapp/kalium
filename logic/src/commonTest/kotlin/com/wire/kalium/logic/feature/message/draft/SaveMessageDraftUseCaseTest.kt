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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.draft.MessageDraft
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SaveMessageDraftUseCaseTest {

    private val testDispatchers: KaliumDispatcher = TestKaliumDispatcher

    @Test
    fun givenConversationId_whenInvokingUseCase_thenShouldCallMessageDraftRepository() = runTest(testDispatchers.io) {
        val (arrangement, saveMessageDraft) = Arrangement()
            .withSaveMessageDraft(MESSAGE_DRAFT, Either.Right(Unit))
            .arrange()

        saveMessageDraft(MESSAGE_DRAFT)

        coVerify {
            arrangement.messageDraftRepository.saveMessageDraft(MESSAGE_DRAFT)
        }.wasInvoked(exactly = once)
    }

    private inner class Arrangement {

        @Mock
        val messageDraftRepository: MessageDraftRepository = mock(MessageDraftRepository::class)

        private val saveMessageDraft by lazy {
            SaveMessageDraftUseCaseImpl(messageDraftRepository, testDispatchers)
        }

        suspend fun withSaveMessageDraft(
            messageDraft: MessageDraft,
            response: Either<StorageFailure, Unit>
        ) = apply {
            coEvery {
                messageDraftRepository.saveMessageDraft(messageDraft)
            }.returns(response)
        }

        fun arrange() = this to saveMessageDraft
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
