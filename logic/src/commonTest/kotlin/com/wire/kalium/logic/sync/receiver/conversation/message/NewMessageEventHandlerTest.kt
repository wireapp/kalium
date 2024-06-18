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
import com.wire.kalium.logic.feature.message.confirmation.ConfirmationDeliveryHandler
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandler
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
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

        coVerify {
            arrangement.proteusMessageUnpacker.unpackProteusMessage(eq(newMessageEvent))
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.proteusMessageUnpacker.unpackProteusMessage(eq(newMessageEvent))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.proteusMessageUnpacker.unpackProteusMessage(eq(newMessageEvent))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMLSEvent_whenHandling_shouldAskMLSUnpackerToDecrypt() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Right(listOf(MessageUnpackResult.HandshakeMessage)))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        newMessageEventHandler.handleNewMLSMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.mlsMessageUnpacker.unpackMlsMessage(eq(newMessageEvent))
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.legalHoldHandler.handleNewMessage(any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.applicationMessageHandler.handleContent(any(), any(), any(), any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUnpackingSuccess_whenHandling_thenHandleContent() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withMLSUnpackerReturning(Either.Right(listOf(applicationMessage)))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        newMessageEventHandler.handleNewMLSMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.mlsMessageUnpacker.unpackMlsMessage(eq(newMessageEvent))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.legalHoldHandler.handleNewMessage(eq(applicationMessage), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.applicationMessageHandler.handleContent(any(), any(), any(), any(), any())
        }.wasInvoked(exactly = once)
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
                )
            ).arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.proteusMessageUnpacker.unpackProteusMessage(eq(newMessageEvent))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }.wasNotInvoked()

        verify {
            arrangement.ephemeralMessageDeletionHandler.startSelfDeletion(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenEphemeralMessage_whenHandling_thenDoNotEnqueueForSelfDelete() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(applicationMessage))
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.proteusMessageUnpacker.unpackProteusMessage(eq(newMessageEvent))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }.wasNotInvoked()

        verify {
            arrangement.ephemeralMessageDeletionHandler.startSelfDeletion(any(), any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.proteusMessageUnpacker.unpackProteusMessage(eq(newMessageEvent))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.legalHoldHandler.handleNewMessage(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenMessageFromSelf_whenHandling_thenDoNotEnqueueForSelfDelete() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(applicationMessage))
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.proteusMessageUnpacker.unpackProteusMessage(eq(newMessageEvent))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.legalHoldHandler.handleNewMessage(eq(applicationMessage), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }.wasNotInvoked()

        verify {
            arrangement.ephemeralMessageDeletionHandler.startSelfDeletion(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenMLSEventFailsWithWrongEpoch_whenHandling_shouldCallWrongEpochHandler() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Left(MLSFailure.WrongEpoch))
            .withVerifyEpoch(Either.Right(Unit))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        newMessageEventHandler.handleNewMLSMessage(newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.staleEpochVerifier.verifyEpoch(eq(newMessageEvent.conversationId), eq(newMessageEvent.timestampIso.toInstant()))
        }.wasInvoked(exactly = once)
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

            coVerify {
                arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
            }.wasNotInvoked()
        }

    private class Arrangement {

        @Mock
        val proteusMessageUnpacker = mock(ProteusMessageUnpacker::class)

        @Mock
        val mlsMessageUnpacker = mock(MLSMessageUnpacker::class)

        @Mock
        val applicationMessageHandler = mock(ApplicationMessageHandler::class)

        @Mock
        val staleEpochVerifier = mock(StaleEpochVerifier::class)

        @Mock
        val ephemeralMessageDeletionHandler = mock(EphemeralMessageDeletionHandler::class)

        @Mock
        val confirmationDeliveryHandler = mock(ConfirmationDeliveryHandler::class)

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
            { conversationId, messageId ->
                confirmationDeliveryHandler.enqueueConfirmationDelivery(conversationId, messageId)
            },
            SELF_USER_ID,
            staleEpochVerifier
        )

        suspend fun withProteusUnpackerReturning(result: Either<CoreFailure, MessageUnpackResult>) = apply {
            coEvery {
                proteusMessageUnpacker.unpackProteusMessage(any())
            }.returns(result)
        }

        suspend fun withHandleLegalHoldSuccess() = apply {
            coEvery {
                legalHoldHandler.handleNewMessage(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withMLSUnpackerReturning(result: Either<CoreFailure, List<MessageUnpackResult>>) =
            apply {
                coEvery {
                    mlsMessageUnpacker.unpackMlsMessage(any())
                }.returns(result)
            }

        suspend fun withVerifyEpoch(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                staleEpochVerifier.verifyEpoch(any(), any())
            }.returns(result)
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
