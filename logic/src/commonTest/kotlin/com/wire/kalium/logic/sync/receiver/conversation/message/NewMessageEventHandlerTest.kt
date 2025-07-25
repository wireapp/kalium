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
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.feature.message.confirmation.ConfirmationDeliveryHandler
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandler
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.util.DateTimeUtil
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class NewMessageEventHandlerTest {

    @Test
    fun givenProteusEvent_whenHandling_shouldAskProteusUnpackerToDecrypt() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withProteusUnpackerReturning(Either.Left(CoreFailure.InvalidEventSenderID))
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any())
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
                            code = ProteusException.Code.DUPLICATE_MESSAGE,
                            intCode = 7
                        )
                    )
                )
            )
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any())
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
                            code = ProteusException.Code.INVALID_SIGNATURE,
                            intCode = 5
                        )
                    )
                )
            )
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any())
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

        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.mlsMessageUnpacker.unpackMlsMessage(any(), eq(newMessageEvent))
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

        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.legalHoldHandler.handleNewMessage(any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.applicationMessageHandler.handleContent(any(), any(), any(), any(), any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUnpackingSuccess_whenHandling_thenHandleContent() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withMLSUnpackerReturning(Either.Right(listOf(applicationMessage)))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.mlsMessageUnpacker.unpackMlsMessage(any(), eq(newMessageEvent))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.legalHoldHandler.handleNewMessage(eq(applicationMessage), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.applicationMessageHandler.handleContent(any(), any(), any(), any(), any(), any())
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

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any())
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

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }.wasNotInvoked()

        verify {
            arrangement.ephemeralMessageDeletionHandler.startSelfDeletion(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenAMessage_whenHandlingSelfMessage_thenEnqueueDeliveryConfirmationShouldNotHappen() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(applicationMessage)).arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")
        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify { arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any()) }.wasInvoked(exactly = once)
        coVerify { arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any()) }.wasNotInvoked()
        coVerify { arrangement.confirmationDeliveryHandler.enqueueConfirmationDelivery(any(), any()) }.wasNotInvoked()
    }

    @Test
    fun givenAMessage_whenHandlingSignalingMessage_thenEnqueueDeliveryConfirmationShouldNotHappen() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(signalingMessage))
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")
        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify { arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any()) }.wasInvoked(exactly = once)
        coVerify { arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any()) }.wasNotInvoked()
        coVerify { arrangement.confirmationDeliveryHandler.enqueueConfirmationDelivery(any(), any()) }.wasNotInvoked()
    }

    @Test
    fun givenAProteusMessage_whenHandling_thenEnqueueDeliveryConfirmation() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(applicationMessage.copy(senderUserId = TestUser.OTHER_USER_ID_2))).arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")
        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify { arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any()) }.wasInvoked(exactly = once)
        coVerify { arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any()) }.wasNotInvoked()
        coVerify { arrangement.confirmationDeliveryHandler.enqueueConfirmationDelivery(any(), any()) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAMLSMessage_whenHandling_thenEnqueueDeliveryConfirmation() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withMLSUnpackerReturning(Either.Right(listOf(applicationMessage.copy(senderUserId = TestUser.OTHER_USER_ID_2))))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())
        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify { arrangement.mlsMessageUnpacker.unpackMlsMessage(any(), eq(newMessageEvent)) }.wasInvoked(exactly = once)
        coVerify { arrangement.confirmationDeliveryHandler.enqueueConfirmationDelivery(any(), any()) }.wasInvoked(exactly = once)
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

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any())
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

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any())
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

        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.staleEpochVerifier.verifyEpoch(
                any(),
                eq(newMessageEvent.conversationId),
                any(),
                eq(newMessageEvent.messageInstant)
            )
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

            newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

            coVerify {
                arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
            }.wasNotInvoked()
        }

    @Test
    fun givenSubconversationId_whenHandlingInformUserFailure_thenShouldNotSendSystemMessage() = runTest {
        val event = TestEvent.newMLSMessageEvent(
            dateTime = DateTimeUtil.currentInstant(),
            subConversationId = SubconversationId("subconversation-id")
        )

        val (arrangement, newMessageEventHandler) = Arrangement()
            .apply {
                withMLSUnpackerReturning(Either.Left(CoreFailure.Unknown(null)))
            }
            .arrange()

        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.applicationMessageHandler.handleDecryptionError(
                eventId = any(),
                conversationId = any(),
                messageInstant = any(),
                senderUserId = any(),
                senderClientId = any(),
                content = any()
            )
        }.wasNotInvoked()
    }

    private class Arrangement: CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val proteusMessageUnpacker = mock(ProteusMessageUnpacker::class)
        val mlsMessageUnpacker = mock(MLSMessageUnpacker::class)
        val applicationMessageHandler = mock(ApplicationMessageHandler::class)
        val staleEpochVerifier = mock(StaleEpochVerifier::class)
        val ephemeralMessageDeletionHandler = mock(EphemeralMessageDeletionHandler::class)
        val confirmationDeliveryHandler = mock(ConfirmationDeliveryHandler::class)
        val legalHoldHandler = mock(LegalHoldHandler::class)
        val resetMlsConversation = mock(ResetMLSConversationUseCase::class)

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
            staleEpochVerifier,
            resetMlsConversation,
        )

        suspend fun withProteusUnpackerReturning(result: Either<CoreFailure, MessageUnpackResult.ApplicationMessage>) = apply {
            coEvery {
                proteusMessageUnpacker.unpackProteusMessage<MessageUnpackResult.ApplicationMessage>(any(), any(), any())
            }.invokes { args ->
                if (result is Either.Right) {
                    val lambda = args[2] as suspend (MessageUnpackResult.ApplicationMessage) -> MessageUnpackResult.ApplicationMessage
                    Either.Right(lambda(result.value))
                } else {
                    result
                }
            }
        }

        suspend fun withHandleLegalHoldSuccess() = apply {
            coEvery {
                legalHoldHandler.handleNewMessage(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withMLSUnpackerReturning(result: Either<CoreFailure, List<MessageUnpackResult>>) =
            apply {
                coEvery {
                    mlsMessageUnpacker.unpackMlsMessage(any(), any())
                }.returns(result)
            }

        suspend fun withVerifyEpoch(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                staleEpochVerifier.verifyEpoch(any(), any(), any(), any())
            }.returns(result)
        }

        fun arrange(block: suspend Arrangement.() -> Unit = {}) = let {
            runBlocking { block() }
            this to newMessageEventHandler
        }
    }

    private companion object {
        val SELF_USER_ID = UserId("selfUserId", "selfDomain")
        val signalingMessage = MessageUnpackResult.ApplicationMessage(
            conversationId = ConversationId("conversationID", "domain"),
            instant = Instant.DISTANT_PAST,
            senderUserId = UserId("otherUserId", "otherUserDomain"),
            senderClientId = ClientId("otherUserClientId"),
            content = ProtoContent.Readable(
                messageUid = "otherMessageUID",
                messageContent = MessageContent.Receipt(
                    type = ReceiptType.READ,
                    messageIds = listOf("messageId1", "messageId2")
                ),
                expectsReadConfirmation = false,
                legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
                expiresAfterMillis = null
            )
        )
        val applicationMessage = MessageUnpackResult.ApplicationMessage(
            ConversationId("conversationID", "domain"),
            Instant.DISTANT_PAST,
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
