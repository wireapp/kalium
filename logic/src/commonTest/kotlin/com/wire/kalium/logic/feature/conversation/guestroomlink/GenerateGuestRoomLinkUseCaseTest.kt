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
package com.wire.kalium.logic.feature.conversation.guestroomlink

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.eventHandler.CodeUpdatedHandlerArrangement
import com.wire.kalium.logic.util.arrangement.eventHandler.CodeUpdatedHandlerArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationGroupRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationGroupRepositoryArrangementImpl
import com.wire.kalium.network.api.authenticated.conversation.guestroomlink.ConversationInviteLinkResponse
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.model.QualifiedID
import io.mockative.any
import io.mockative.coVerify
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class GenerateGuestRoomLinkUseCaseTest {

    @Test
    fun givenRepositoryReturnsSuccess_whenTryingToGenerateAGuestRoomLink_ThenReturnSuccess() = runTest {

        val eventDTO = EventContentDTO.Conversation.CodeUpdated(
            qualifiedConversation = conversationId.toApi(),
            qualifiedFrom = QualifiedID("userId", "domain"),
            data = ConversationInviteLinkResponse(
                uri = "uri",
                code = "code",
                key = "key",
                hasPassword = false
            )
        )

        val (arrangement, useCase) = Arrangement()
            .arrange {
                withGenerateGuestRoomLink(Either.Right(eventDTO))
                withHandleCodeUpdatedEvent(Either.Right(Unit))
            }

        useCase(conversationId, null).also { result ->
            assertIs<GenerateGuestRoomLinkResult.Success>(result)
        }

        coVerify {
            arrangement.conversationGroupRepository.generateGuestRoomLink(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.codeUpdatedHandler.handle(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryReturnsFailure_whenTryingToGenerateAGuestRoomLink_ThenReturnError() = runTest {

        val (arrangement, useCase) = Arrangement()
            .arrange {
                withGenerateGuestRoomLink(Either.Left(NetworkFailure.NoNetworkConnection(null)))
            }
        useCase(conversationId, null).also { result ->
            assertIs<GenerateGuestRoomLinkResult.Failure>(result)
        }

        coVerify {
            arrangement.conversationGroupRepository.generateGuestRoomLink(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.codeUpdatedHandler.handle(any())
        }.wasNotInvoked()
    }

    companion object {
        private val conversationId = ConversationId("value", "domain")
    }

    private class Arrangement :
        ConversationGroupRepositoryArrangement by ConversationGroupRepositoryArrangementImpl(),
        CodeUpdatedHandlerArrangement by CodeUpdatedHandlerArrangementImpl() {

        private val generateGuestRoomLink: GenerateGuestRoomLinkUseCase = GenerateGuestRoomLinkUseCaseImpl(
            conversationGroupRepository,
            codeUpdatedHandler
        )

        fun arrange(block: suspend Arrangement.() -> Unit) = run {
            runBlocking { block() }
            this to generateGuestRoomLink
        }
    }
}
