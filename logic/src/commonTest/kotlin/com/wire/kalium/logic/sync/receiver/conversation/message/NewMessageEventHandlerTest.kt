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

package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandler
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
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
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.test.Test

class NewMessageEventHandlerTest {

    @Test
    fun givenProteusEvent_whenHandling_shouldAskProteusUnpackerToDecrypt() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withProteusUnpackerReturning(Either.Right(MessageUnpackResult.HandshakeMessage))
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

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

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

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

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

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
            .withMLSUnpackerReturning(Either.Right(listOf(MessageUnpackResult.HandshakeMessage)))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        newMessageEventHandler.handleNewMLSMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.mlsMessageUnpacker)
            .suspendFunction(arrangement.mlsMessageUnpacker::unpackMlsMessage)
            .with(eq(newMessageEvent))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAnMLSMessageWithLegalHoldUnknown_whenHandlingIt_thenDoNotUpdateLegalHoldStatus() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withMLSUnpackerReturning(
                Either.Right(
                    listOf(
                        applicationMessage.copy(
                            content = applicationMessage.content.copy(
                                legalHoldStatus = Conversation.LegalHoldStatus.UNKNOWN
                            )
                        )
                    )
                )
            )
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        newMessageEventHandler.handleNewMLSMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.legalHoldHandler)
            .suspendFunction(arrangement.legalHoldHandler::handleNewMessage)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.applicationMessageHandler)
            .suspendFunction(arrangement.applicationMessageHandler::handleContent)
            .with(any(), any(), any(), any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUnpackingSuccess_whenHandling_thenHandleContent() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withMLSUnpackerReturning(Either.Right(listOf(applicationMessage)))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        newMessageEventHandler.handleNewMLSMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.mlsMessageUnpacker)
            .suspendFunction(arrangement.mlsMessageUnpacker::unpackMlsMessage)
            .with(eq(newMessageEvent))
            .wasInvoked(exactly = once)

        verify(arrangement.legalHoldHandler)
            .suspendFunction(arrangement.legalHoldHandler::handleNewMessage)
            .with(eq(applicationMessage))
            .wasInvoked(exactly = once)

        verify(arrangement.applicationMessageHandler)
            .suspendFunction(arrangement.applicationMessageHandler::handleContent)
            .with(any(), any(), any(), any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenEphemeralMessageFromSelf_whenHandling_thenEnqueueForSelfDelete() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(
                Either.Right(
                    applicationMessage.copy(
                        content = applicationMessage.content.copy(expiresAfterMillis = 123L)
                    )
                ))
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

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
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(applicationMessage))
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

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
    fun givenAMessageWithUnknownLegalHoldStatus_whenHandlingIt_thenDoNotUpdateCurrentLegalHold() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(
                Either.Right(
                    applicationMessage.copy(
                        content = applicationMessage.content.copy(
                            legalHoldStatus = Conversation.LegalHoldStatus.UNKNOWN
                        )
                    )
                )
            )
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.proteusMessageUnpacker)
            .suspendFunction(arrangement.proteusMessageUnpacker::unpackProteusMessage)
            .with(eq(newMessageEvent))
            .wasInvoked(exactly = once)

        verify(arrangement.legalHoldHandler)
            .suspendFunction(arrangement.legalHoldHandler::handleNewMessage)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenMessageFromSelf_whenHandling_thenDoNotEnqueueForSelfDelete() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(applicationMessage))
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.proteusMessageUnpacker)
            .suspendFunction(arrangement.proteusMessageUnpacker::unpackProteusMessage)
            .with(eq(newMessageEvent))
            .wasInvoked(exactly = once)

        verify(arrangement.legalHoldHandler)
            .suspendFunction(arrangement.legalHoldHandler::handleNewMessage)
            .with(eq(applicationMessage))
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
    fun givenMLSEventFailsWithWrongEpoch_whenHandling_shouldCallWrongEpochHandler() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Left(MLSFailure.WrongEpoch))
            .withVerifyEpoch(Either.Right(Unit))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        newMessageEventHandler.handleNewMLSMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.staleEpochVerifier)
            .suspendFunction(arrangement.staleEpochVerifier::verifyEpoch)
            .with(eq(newMessageEvent.conversationId), eq(newMessageEvent.timestampIso.toInstant()))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMLSEventFailsWithWrongEpoch_whenHandling_shouldNotPersistDecryptionErrorMessage() =
        runTest {
            val (arrangement, newMessageEventHandler) = Arrangement()
                .withMLSUnpackerReturning(Either.Left(MLSFailure.WrongEpoch))
                .withVerifyEpoch(Either.Right(Unit))
                .arrange()

            val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

            newMessageEventHandler.handleNewMLSMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

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
        val staleEpochVerifier = mock(classOf<StaleEpochVerifier>())

        @Mock
        val ephemeralMessageDeletionHandler = mock(EphemeralMessageDeletionHandler::class)

        @Mock
        val legalHoldHandler = mock(LegalHoldHandler::class)

        private val newMessageEventHandler: NewMessageEventHandler = NewMessageEventHandlerImpl(
            proteusMessageUnpacker,
            mlsMessageUnpacker,
            applicationMessageHandler,
            legalHoldHandler,
            { conversationId, messageId ->
                ephemeralMessageDeletionHandler.startSelfDeletion(
                    conversationId,
                    messageId
                )
            },
            SELF_USER_ID,
            staleEpochVerifier
        )

        fun withProteusUnpackerReturning(result: Either<CoreFailure, MessageUnpackResult>) = apply {
            given(proteusMessageUnpacker)
                .suspendFunction(proteusMessageUnpacker::unpackProteusMessage)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withHandleLegalHoldSuccess() = apply {
            given(legalHoldHandler)
                .suspendFunction(legalHoldHandler::handleNewMessage)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withMLSUnpackerReturning(result: Either<CoreFailure, List<MessageUnpackResult>>) =
            apply {
                given(mlsMessageUnpacker)
                    .suspendFunction(mlsMessageUnpacker::unpackMlsMessage)
                    .whenInvokedWith(any())
                    .thenReturn(result)
            }

        fun withVerifyEpoch(result: Either<CoreFailure, Unit>) = apply {
            given(staleEpochVerifier)
                .suspendFunction(staleEpochVerifier::verifyEpoch)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun arrange() = this to newMessageEventHandler

    }

    private companion object {
        val SELF_USER_ID = UserId("selfUserId", "selfDomain")
        val applicationMessage = MessageUnpackResult.ApplicationMessage(
            ConversationId("conversationID", "domain"),
            Instant.DISTANT_PAST.toIsoDateTimeString(),
            SELF_USER_ID,
            ClientId("clientID"),
            ProtoContent.Readable(
                messageUid = "messageUID",
                messageContent = MessageContent.Text(value = "messageContent"),
                expectsReadConfirmation = false,
                legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
                expiresAfterMillis = null
            )
        )
    }
}
