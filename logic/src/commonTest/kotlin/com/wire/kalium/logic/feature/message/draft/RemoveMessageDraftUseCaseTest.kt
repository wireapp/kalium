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
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RemoveMessageDraftUseCaseTest {

    private val testDispatchers: KaliumDispatcher = TestKaliumDispatcher

    @Test
    fun givenConversationId_whenInvokingUseCase_thenShouldCallMessageDraftRepository() = runTest(testDispatchers.io) {
        val (arrangement, removeMessageDraft) = Arrangement()
            .withRemoveMessageDraft(CONVERSATION_ID)
            .arrange()

        removeMessageDraft(CONVERSATION_ID)

        coVerify {
            arrangement.messageDraftRepository.removeMessageDraft(CONVERSATION_ID)
        }.wasInvoked(exactly = once)
    }

    private inner class Arrangement {
        val messageDraftRepository: MessageDraftRepository = mock(MessageDraftRepository::class)

        private val removeMessageDraft by lazy {
            RemoveMessageDraftUseCaseImpl(messageDraftRepository, testDispatchers)
        }

        suspend fun withRemoveMessageDraft(conversationId: ConversationId) = apply {
            coEvery {
                messageDraftRepository.removeMessageDraft(conversationId)
            }.returns(Unit)
        }

        fun arrange() = this to removeMessageDraft
    }

    private companion object {
        val CONVERSATION_ID = TestConversation.ID
    }
}
