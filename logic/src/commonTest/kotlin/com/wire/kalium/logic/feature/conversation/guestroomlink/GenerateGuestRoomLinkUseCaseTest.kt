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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdatedHandler
import com.wire.kalium.network.api.authenticated.conversation.guestroomlink.ConversationInviteLinkResponse
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.model.QualifiedID
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationGroupRepository.generateGuestRoomLink(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.codeUpdatedHandler.handle(any())
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationGroupRepository.generateGuestRoomLink(any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.codeUpdatedHandler.handle(any())
        }
    }

    companion object {
        private val conversationId = ConversationId("value", "domain")
    }

    private class Arrangement {
        val conversationGroupRepository = mock<ConversationGroupRepository>(mode = MockMode.autoUnit)
        val codeUpdatedHandler = mock<CodeUpdatedHandler>(mode = MockMode.autoUnit)

        private val generateGuestRoomLink: GenerateGuestRoomLinkUseCase = GenerateGuestRoomLinkUseCaseImpl(
            conversationGroupRepository,
            codeUpdatedHandler
        )

        suspend fun arrange(block: suspend Arrangement.() -> Unit) = run {
            block()
            this to generateGuestRoomLink
        }

        suspend fun withGenerateGuestRoomLink(result: Either<NetworkFailure, EventContentDTO.Conversation.CodeUpdated>) {
            everySuspend { conversationGroupRepository.generateGuestRoomLink(any(), any()) } returns result
        }

        suspend fun withHandleCodeUpdatedEvent(result: Either<StorageFailure, Unit>) {
            everySuspend { codeUpdatedHandler.handle(any<Event.Conversation.CodeUpdated>()) } returns result
        }
    }
}
