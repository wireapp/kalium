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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationRepositoryTest
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser.USER_ID
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.network.api.authenticated.conversation.ConversationNameUpdateEvent
import com.wire.kalium.network.api.authenticated.conversation.ConversationRenameResponse
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertIs

class RenameConversationUseCaseTest {

    @Test
    fun givenAConversation_WhenChangingNameIsSuccessful_ThenReturnSuccess() = runTest {
        val (arrangement, renameConversation) = Arrangement()
            .withRenameConversationIs(Either.Right(CONVERSATION_RENAME_RESPONSE))
            .arrange()

        val result = renameConversation(TestConversation.ID, "new_name")

        assertIs<RenamingResult.Success>(result)

        coVerify {
            arrangement.conversationRepository.changeConversationName(eq(TestConversation.ID), eq("new_name"))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.renamedConversationEventHandler.handle(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversation_WhenChangingNameFails_ThenReturnFailure() = runTest {
        val (arrangement, renameConversation) = Arrangement()
            .withRenameConversationIs(Either.Left(CoreFailure.Unknown(RuntimeException("Error!"))))
            .arrange()

        val result = renameConversation(TestConversation.ID, "new_name")

        assertIs<RenamingResult.Failure>(result)

        coVerify {
            arrangement.conversationRepository.changeConversationName(eq(TestConversation.ID), eq("new_name"))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val persistMessage = mock(PersistMessageUseCase::class)

        @Mock
        val renamedConversationEventHandler = mock(RenamedConversationEventHandler::class)

        val selfUserId = USER_ID

        private val renameConversation = RenameConversationUseCaseImpl(
            conversationRepository,
            persistMessage,
            renamedConversationEventHandler,
            selfUserId
        )

        suspend fun withRenameConversationIs(either: Either<CoreFailure, ConversationRenameResponse>) = apply {
            coEvery {
                conversationRepository.changeConversationName(any(), any())
            }.returns(either)
        }

        fun arrange() = this to renameConversation
    }

    companion object {
        private val CONVERSATION_RENAME_RESPONSE = ConversationRenameResponse.Changed(
            EventContentDTO.Conversation.ConversationRenameDTO(
                ConversationRepositoryTest.CONVERSATION_ID.toApi(),
                ConversationRepositoryTest.USER_ID.toApi(),
                Instant.DISTANT_PAST,
                ConversationNameUpdateEvent("newName")
            )
        )
    }
}
