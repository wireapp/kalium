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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import io.mockative.any
import io.mockative.coVerify
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class DataTransferEventHandlerTest {

    @Test
    fun givenSelfUserDataTransferContent_whenHandlingEvent_thenSetTrackingIdentifier() = runTest {
        // given
        val (arrangement, handler) = Arrangement().arrange {
            withGetTrackingIdentifier(null)
            withSetTrackingIdentifier()
        }

        // when
        handler.handle(
            message = MESSAGE,
            messageContent = MESSAGE_CONTENT
        )

        // then
        coVerify {
            arrangement.userConfigRepository.setCurrentTrackingIdentifier(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenOtherUserSentDataTransferContent_whenHandlingEvent_thenTrackingIdentifierIsNotSet() = runTest {
        // given
        val (arrangement, handler) = Arrangement().arrange {
            withSetTrackingIdentifier()
        }

        // when
        handler.handle(
            message = MESSAGE.copy(senderUserId = OTHER_USER_ID),
            messageContent = MESSAGE_CONTENT
        )

        // then
        coVerify {
            arrangement.userConfigRepository.setCurrentTrackingIdentifier(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenSelfUserDataTransferContentWithNullIdentifier_whenHandlingEvent_thenTrackingIdentifierIsNotSet() = runTest {
        // given
        val (arrangement, handler) = Arrangement().arrange {
            withSetTrackingIdentifier()
        }

        // when
        handler.handle(
            message = MESSAGE,
            messageContent = MESSAGE_CONTENT.copy(trackingIdentifier = null)
        )

        // then
        coVerify {
            arrangement.userConfigRepository.setCurrentTrackingIdentifier(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenSelfUserHasTrackingIdentifier_whenReceivingNewTrackingIdentifier_thenMoveCurrentToPreviousAndUpdate() = runTest {
        // given
        val currentIdentifier = "abcd-1234"
        val newIdentifier = "efgh-5678"
        val (arrangement, handler) = Arrangement().arrange {
            withGetTrackingIdentifier(currentIdentifier)
        }

        // when
        handler.handle(
            message = MESSAGE,
            messageContent = MESSAGE_CONTENT.copy(
                trackingIdentifier = MESSAGE_CONTENT.trackingIdentifier?.copy(
                    identifier = newIdentifier
                )
            )
        )

        // then
        coVerify {
            arrangement.userConfigRepository.setPreviousTrackingIdentifier(currentIdentifier)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.userConfigRepository.setCurrentTrackingIdentifier(newIdentifier)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCurrentIdentifierIsTheSame_whenReceivingNewTrackingIdentifier_thenDoNotUpdateTrackingIdentifier() = runTest {
        // given
        val (arrangement, handler) = Arrangement().arrange {
            withGetTrackingIdentifier(MESSAGE_CONTENT.trackingIdentifier?.identifier)
        }

        // when
        handler.handle(
            message = MESSAGE,
            messageContent = MESSAGE_CONTENT
        )

        // then
        coVerify {
            arrangement.userConfigRepository.setPreviousTrackingIdentifier(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.userConfigRepository.setCurrentTrackingIdentifier(any())
        }.wasNotInvoked()
    }

    private companion object {
        val CONVERSATION_ID = ConversationId("conversationId", "domain")
        val SELF_USER_ID = UserId("selfUserId", "domain")
        val OTHER_USER_ID = UserId("otherUserId", "domain")

        val MESSAGE_CONTENT = MessageContent.DataTransfer(
            trackingIdentifier = MessageContent.DataTransfer.TrackingIdentifier(
                identifier = "abcd-1234-efgh-5678"
            )
        )
        val MESSAGE = Message.Signaling(
            id = "messageId",
            content = MESSAGE_CONTENT,
            conversationId = CONVERSATION_ID,
            date = Instant.DISTANT_PAST,
            senderUserId = SELF_USER_ID,
            senderClientId = ClientId("deviceId"),
            status = Message.Status.Sent,
            isSelfMessage = false,
            expirationData = null,
        )
    }

    private class Arrangement : UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl() {
        private val handler: DataTransferEventHandler = DataTransferEventHandlerImpl(
            selfUserId = SELF_USER_ID,
            userConfigRepository = userConfigRepository
        )

        fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, DataTransferEventHandler> {
            runBlocking { block() }
            return this to handler
        }
    }
}
