/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandler
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewMessageEventHandlerTest {

    @Test
    fun givenProteusEvent_whenHandling_shouldAskProteusUnpackerToDecrypt() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withProteusUnpackerReturning(Either.Right(MessageUnpackResult.HandshakeMessage))
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent)

        verify(arrangement.proteusMessageUnpacker)
            .suspendFunction(arrangement.proteusMessageUnpacker::unpackProteusMessage)
            .with(eq(newMessageEvent))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusDUPLICATED_MESSAGE_whenHandling_thenErrorShouldBeIgnored() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withProteusUnpackerReturning(
                Either.Left(
                    ProteusFailure(
                        ProteusException(
                            message = null,
                            code = ProteusException.Code.DUPLICATE_MESSAGE
                        )
                    )
                )
            )
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent)

        verify(arrangement.proteusMessageUnpacker)
            .suspendFunction(arrangement.proteusMessageUnpacker::unpackProteusMessage)
            .with(eq(newMessageEvent))
            .wasInvoked(exactly = once)

        verify(arrangement.applicationMessageHandler)
            .suspendFunction(arrangement.applicationMessageHandler::handleDecryptionError)
            .with(any(), any(), any(), any(), any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenProteus_whenHandling_thenErrorShouldBeHandled() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withProteusUnpackerReturning(
                Either.Left(
                    ProteusFailure(
                        ProteusException(
                            message = null,
                            code = ProteusException.Code.INVALID_SIGNATURE
                        )
                    )
                )
            )
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent)

        verify(arrangement.proteusMessageUnpacker)
            .suspendFunction(arrangement.proteusMessageUnpacker::unpackProteusMessage)
            .with(eq(newMessageEvent))
            .wasInvoked(exactly = once)

        verify(arrangement.applicationMessageHandler)
            .suspendFunction(arrangement.applicationMessageHandler::handleDecryptionError)
            .with(any(), any(), any(), any(), any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMLSEvent_whenHandling_shouldAskMLSUnpackerToDecrypt() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Right(MessageUnpackResult.HandshakeMessage))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        newMessageEventHandler.handleNewMLSMessage(newMessageEvent)

        verify(arrangement.mlsMessageUnpacker)
            .suspendFunction(arrangement.mlsMessageUnpacker::unpackMlsMessage)
            .with(eq(newMessageEvent))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenEphemeralMessageFromSelf_whenHandling_thenEnqueueForSelfDelete() = runTest {
        val conversationID = ConversationId("conversationID", "domain")
        val senderUserId = SELF_USER_ID
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withProteusUnpackerReturning(
                Either.Right(
                    MessageUnpackResult.ApplicationMessage(
                        conversationID,
                        Instant.DISTANT_PAST.toIsoDateTimeString(),
                        senderUserId,
                        ClientId("clientID"),
                        ProtoContent.Readable(
                            messageUid = "messageUID",
                             messageContent = MessageContent.Text(
                                 value = "messageContent"
                             ),
                            expectsReadConfirmation = false,
                            expiresAfterMillis = 123L
                        )
                    )
                )
            )
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent)

        verify(arrangement.proteusMessageUnpacker)
            .suspendFunction(arrangement.proteusMessageUnpacker::unpackProteusMessage)
            .with(eq(newMessageEvent))
            .wasInvoked(exactly = once)

        verify(arrangement.applicationMessageHandler)
            .suspendFunction(arrangement.applicationMessageHandler::handleDecryptionError)
            .with(any(), any(), any(), any(), any(), any())
            .wasNotInvoked()

        verify(arrangement.ephemeralMessageDeletionHandler)
            .function(arrangement.ephemeralMessageDeletionHandler::startSelfDeletion)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenEphemeralMessage_whenHandling_thenDoNotEnqueueForSelfDelete() = runTest {
        val conversationID = ConversationId("conversationID", "domain")
        val senderUserId = UserId("otherUserId", "domain")
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withProteusUnpackerReturning(
                Either.Right(
                    MessageUnpackResult.ApplicationMessage(
                        conversationID,
                        Instant.DISTANT_PAST.toIsoDateTimeString(),
                        senderUserId,
                        ClientId("clientID"),
                        ProtoContent.Readable(
                            messageUid = "messageUID",
                            messageContent = MessageContent.Text(
                                value = "messageContent"
                            ),
                            expectsReadConfirmation = false,
                            expiresAfterMillis = 123L
                        )
                    )
                )
            )
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent)

        verify(arrangement.proteusMessageUnpacker)
            .suspendFunction(arrangement.proteusMessageUnpacker::unpackProteusMessage)
            .with(eq(newMessageEvent))
            .wasInvoked(exactly = once)

        verify(arrangement.applicationMessageHandler)
            .suspendFunction(arrangement.applicationMessageHandler::handleDecryptionError)
            .with(any(), any(), any(), any(), any(), any())
            .wasNotInvoked()

        verify(arrangement.ephemeralMessageDeletionHandler)
            .function(arrangement.ephemeralMessageDeletionHandler::startSelfDeletion)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenMessageFromSelf_whenHandling_thenDoNotEnqueueForSelfDelete() = runTest {
        val conversationID = ConversationId("conversationID", "domain")
        val senderUserId = SELF_USER_ID
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withProteusUnpackerReturning(
                Either.Right(
                    MessageUnpackResult.ApplicationMessage(
                        conversationID,
                        Instant.DISTANT_PAST.toIsoDateTimeString(),
                        senderUserId,
                        ClientId("clientID"),
                        ProtoContent.Readable(
                            messageUid = "messageUID",
                            messageContent = MessageContent.Text(
                                value = "messageContent"
                            ),
                            expectsReadConfirmation = false,
                            expiresAfterMillis = null
                        )
                    )
                )
            )
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent)

        verify(arrangement.proteusMessageUnpacker)
            .suspendFunction(arrangement.proteusMessageUnpacker::unpackProteusMessage)
            .with(eq(newMessageEvent))
            .wasInvoked(exactly = once)

        verify(arrangement.applicationMessageHandler)
            .suspendFunction(arrangement.applicationMessageHandler::handleDecryptionError)
            .with(any(), any(), any(), any(), any(), any())
            .wasNotInvoked()

        verify(arrangement.ephemeralMessageDeletionHandler)
            .function(arrangement.ephemeralMessageDeletionHandler::startSelfDeletion)
            .with(any(), any())
            .wasNotInvoked()
    }

    private companion object {
        val SELF_USER_ID = UserId("selfUserId", "selfDomain")
    }

    @Test
    fun givenMLSEventFailsWithWrongEpoch_whenHandling_shouldCallWrongEpochHandler() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Left(MLSFailure.WrongEpoch))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        newMessageEventHandler.handleNewMLSMessage(newMessageEvent)

        verify(arrangement.mlsWrongEpochHandler)
            .suspendFunction(arrangement.mlsWrongEpochHandler::onMLSWrongEpoch)
            .with(eq(newMessageEvent.conversationId),eq(newMessageEvent.timestampIso))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMLSEventFailsWithWrongEpoch_whenHandling_shouldNotPersistDecryptionErrorMessage() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Left(MLSFailure.WrongEpoch))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        newMessageEventHandler.handleNewMLSMessage(newMessageEvent)

        verify(arrangement.applicationMessageHandler)
            .suspendFunction(arrangement.applicationMessageHandler::handleDecryptionError)
            .with(any())
            .wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val proteusMessageUnpacker = mock(classOf<ProteusMessageUnpacker>())

        @Mock
        val mlsMessageUnpacker = mock(classOf<MLSMessageUnpacker>())

        @Mock
        val applicationMessageHandler = configure(mock(classOf<ApplicationMessageHandler>())) {
            stubsUnitByDefault = true
        }

        @Mock
        val mlsWrongEpochHandler = mock(classOf<MLSWrongEpochHandler>())

        @Mock
        val ephemeralMessageDeletionHandler = mock(EphemeralMessageDeletionHandler::class)

        private val newMessageEventHandler: NewMessageEventHandler = NewMessageEventHandlerImpl(
            proteusMessageUnpacker,
            mlsMessageUnpacker,
            applicationMessageHandler,
            { conversationId, messageId -> ephemeralMessageDeletionHandler.startSelfDeletion(conversationId, messageId) },
            SELF_USER_ID,
            mlsWrongEpochHandler
        )

        fun withProteusUnpackerReturning(result: Either<CoreFailure, MessageUnpackResult>) = apply {
            given(proteusMessageUnpacker)
                .suspendFunction(proteusMessageUnpacker::unpackProteusMessage)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withMLSUnpackerReturning(result: Either<CoreFailure, MessageUnpackResult>) = apply {
            given(mlsMessageUnpacker)
                .suspendFunction(mlsMessageUnpacker::unpackMlsMessage)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun arrange() = this to newMessageEventHandler

    }
}
