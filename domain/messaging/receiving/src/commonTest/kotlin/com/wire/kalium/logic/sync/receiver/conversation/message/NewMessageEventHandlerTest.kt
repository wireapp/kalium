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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.incremental.EventSource
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NewMessageEventHandlerTest {

    @Test
    fun givenPendingSideEffects_whenFlushing_thenDelegateToApplicationMessageHandler() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement().arrange()

        newMessageEventHandler.flushPendingSideEffects()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.applicationMessageHandler.flushPendingSideEffects()
        }
    }

    @Test
    fun givenProteusEvent_whenHandling_shouldAskProteusUnpackerToDecrypt() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withProteusUnpackerReturning(Either.Left(CoreFailure.InvalidEventSenderID))
            .arrange()

        val newMessageEvent = newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any())
        }
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

        val newMessageEvent = newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenProteusUnknownErrorContainingDuplicateMessage_whenHandling_thenErrorShouldBeHandled() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withProteusUnpackerReturning(
                Either.Left(
                    ProteusFailure(
                        ProteusException(
                            message = "exception=com.wire.crypto.ProteusException\$DuplicateMessage: ",
                            code = ProteusException.Code.UNKNOWN_ERROR,
                            intCode = null
                        )
                    )
                )
            )
            .arrange()

        val newMessageEvent = newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }
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

        val newMessageEvent = newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenProteusInformUserFailure_whenHandling_thenPersistResolvedDecryptionFailure() = runTest {
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

        val newMessageEvent = newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.applicationMessageHandler.handleDecryptionError(
                eventId = eq(newMessageEvent.id),
                conversationId = eq(newMessageEvent.conversationId),
                messageInstant = eq(newMessageEvent.messageInstant),
                senderUserId = eq(newMessageEvent.senderUserId),
                senderClientId = eq(newMessageEvent.senderClientId),
                content = eq(
                    MessageContent.FailedDecryption(
                        encodedData = newMessageEvent.encryptedExternalContent?.data,
                        errorCode = 5,
                        isDecryptionResolved = true,
                        senderUserId = newMessageEvent.senderUserId,
                        clientId = ClientId(newMessageEvent.senderClientId.value)
                    )
                )
            )
        }
    }

    @Test
    fun givenProteusRecoverSessionFailure_whenHandling_thenPersistUnresolvedDecryptionFailure() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withProteusUnpackerReturning(
                Either.Left(
                    ProteusFailure(
                        ProteusException(
                            message = null,
                            code = ProteusException.Code.SESSION_NOT_FOUND,
                            intCode = 2
                        )
                    )
                )
            )
            .arrange()

        val newMessageEvent = newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.applicationMessageHandler.handleDecryptionError(
                eventId = eq(newMessageEvent.id),
                conversationId = eq(newMessageEvent.conversationId),
                messageInstant = eq(newMessageEvent.messageInstant),
                senderUserId = eq(newMessageEvent.senderUserId),
                senderClientId = eq(newMessageEvent.senderClientId),
                content = eq(
                    MessageContent.FailedDecryption(
                        encodedData = newMessageEvent.encryptedExternalContent?.data,
                        errorCode = 2,
                        isDecryptionResolved = false,
                        senderUserId = newMessageEvent.senderUserId,
                        clientId = ClientId(newMessageEvent.senderClientId.value)
                    )
                )
            )
        }
    }

    @Test
    fun givenMLSEvent_whenHandling_shouldAskMLSUnpackerToDecrypt() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Right(listOf(MessageUnpackResult.HandshakeMessage)))
            .arrange()

        val newMessageEvent = newMLSMessageEvent(TEST_INSTANT)

        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsMessageUnpacker.unpackMlsMessage(any(), eq(newMessageEvent))
        }
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

        val newMessageEvent = newMLSMessageEvent(TEST_INSTANT)

        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        assertTrue(arrangement.legalHoldCalls.isEmpty())

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.applicationMessageHandler.handleContent(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenUnpackingSuccess_whenHandling_thenHandleContent() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withMLSUnpackerReturning(Either.Right(listOf(applicationMessage)))
            .arrange()

        val newMessageEvent = newMLSMessageEvent(TEST_INSTANT)

        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsMessageUnpacker.unpackMlsMessage(any(), eq(newMessageEvent))
        }

        assertEquals(listOf(LegalHoldCall(applicationMessage, isLive = true)), arrangement.legalHoldCalls)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.applicationMessageHandler.handleContent(any(), any(), any(), any(), any(), any())
        }
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

        val newMessageEvent = newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }

        assertEquals(
            listOf(MessageActionCall(applicationMessage.conversationId, applicationMessage.content.messageUid)),
            arrangement.selfDeletionCalls
        )
    }

    @Test
    fun givenEphemeralMessage_whenHandling_thenDoNotEnqueueForSelfDelete() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(applicationMessage))
            .arrange()

        val newMessageEvent = newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }

        assertTrue(arrangement.selfDeletionCalls.isEmpty())
    }

    @Test
    fun givenAMessage_whenHandlingSelfMessage_thenEnqueueDeliveryConfirmationShouldNotHappen() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(applicationMessage)).arrange()

        val newMessageEvent = newMessageEvent("encryptedContent")
        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) { arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any()) }
        verifySuspend(VerifyMode.not) { arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any()) }
        assertTrue(arrangement.confirmationDeliveryCalls.isEmpty())
    }

    @Test
    fun givenAMessage_whenHandlingSignalingMessage_thenEnqueueDeliveryConfirmationShouldNotHappen() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(signalingMessage))
            .arrange()

        val newMessageEvent = newMessageEvent("encryptedContent")
        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) { arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any()) }
        verifySuspend(VerifyMode.not) { arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any()) }
        assertTrue(arrangement.confirmationDeliveryCalls.isEmpty())
    }

    @Test
    fun givenAProteusMessage_whenHandling_thenEnqueueDeliveryConfirmation() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(applicationMessage.copy(senderUserId = OTHER_USER_ID))).arrange()

        val newMessageEvent = newMessageEvent("encryptedContent")
        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) { arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any()) }
        verifySuspend(VerifyMode.not) { arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any()) }
        assertEquals(
            listOf(MessageActionCall(applicationMessage.conversationId, applicationMessage.content.messageUid)),
            arrangement.confirmationDeliveryCalls
        )
    }

    @Test
    fun givenAMLSMessage_whenHandling_thenEnqueueDeliveryConfirmation() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withMLSUnpackerReturning(Either.Right(listOf(applicationMessage.copy(senderUserId = OTHER_USER_ID))))
            .arrange()

        val newMessageEvent = newMLSMessageEvent(TEST_INSTANT)
        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsMessageUnpacker.unpackMlsMessage(any(), eq(newMessageEvent)) }
        assertEquals(
            listOf(MessageActionCall(applicationMessage.conversationId, applicationMessage.content.messageUid)),
            arrangement.confirmationDeliveryCalls
        )
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

        val newMessageEvent = newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any())
        }

        assertTrue(arrangement.legalHoldCalls.isEmpty())
    }

    @Test
    fun givenMessageFromSelf_whenHandling_thenDoNotEnqueueForSelfDelete() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(applicationMessage))
            .arrange()

        val newMessageEvent = newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), eq(newMessageEvent), any())
        }

        assertEquals(listOf(LegalHoldCall(applicationMessage, isLive = true)), arrangement.legalHoldCalls)

        verifySuspend(VerifyMode.not) {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }

        assertTrue(arrangement.selfDeletionCalls.isEmpty())
    }

    @Test
    fun givenMLSEventFailsWithWrongEpoch_whenHandling_shouldCallWrongEpochHandler() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Left(MLSFailure.WrongEpoch))
            .withVerifyEpoch(Either.Right(Unit))
            .arrange()

        val newMessageEvent = newMLSMessageEvent(TEST_INSTANT)

        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        assertEquals(
            listOf(
                StaleEpochCall(
                    transactionContext = arrangement.transactionContext,
                    conversationId = newMessageEvent.conversationId,
                    subConversationId = newMessageEvent.subconversationId,
                    timestamp = newMessageEvent.messageInstant,
                )
            ),
            arrangement.staleEpochCalls
        )
    }

    @Test
    fun givenMLSEventFailsWithWrongEpoch_whenHandling_shouldNotPersistDecryptionErrorMessage() =
        runTest {
            val (arrangement, newMessageEventHandler) = Arrangement()
                .withMLSUnpackerReturning(Either.Left(MLSFailure.WrongEpoch))
                .withVerifyEpoch(Either.Right(Unit))
                .arrange()

            val newMessageEvent = newMLSMessageEvent(TEST_INSTANT)

            newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

            verifySuspend(VerifyMode.not) {
                arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun givenMLSEventFailsWithInvalidLeafNodeIndex_whenHandling_thenResetConversation() = runTest {
        val newMessageEvent = newMLSMessageEvent(TEST_INSTANT)
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Left(NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeIndex))
            .arrange()

        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, liveDeliveryInfo)

        assertEquals(
            listOf(ResetCall(newMessageEvent.conversationId, arrangement.transactionContext)),
            arrangement.resetCalls,
        )
    }

    @Test
    fun givenSubconversationId_whenHandlingInformUserFailure_thenShouldNotSendSystemMessage() = runTest {
        val event = newMLSMessageEvent(
            dateTime = TEST_INSTANT,
            subConversationId = SubconversationId("subconversation-id")
        )

        val (arrangement, newMessageEventHandler) = Arrangement()
            .apply {
                withMLSUnpackerReturning(Either.Left(CoreFailure.Unknown(null)))
            }
            .arrange()

        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, event, liveDeliveryInfo)

        verifySuspend(VerifyMode.not) {
            arrangement.applicationMessageHandler.handleDecryptionError(
                eventId = any(),
                conversationId = any(),
                messageInstant = any(),
                senderUserId = any(),
                senderClientId = any(),
                content = any()
            )
        }
    }

    @Test
    fun givenParentConversation_whenHandlingInformUserFailure_thenShouldPersistDecryptionError() = runTest {
        val event = newMLSMessageEvent(dateTime = TEST_INSTANT)
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Left(CoreFailure.Unknown(null)))
            .arrange()

        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, event, liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.applicationMessageHandler.handleDecryptionError(
                eventId = eq(event.id),
                conversationId = eq(event.conversationId),
                messageInstant = eq(event.messageInstant),
                senderUserId = eq(event.senderUserId),
                senderClientId = any(),
                content = matches { it.senderUserId == event.senderUserId && !it.isDecryptionResolved }
            )
        }
    }

    @Test
    fun givenResetConversationFailure_whenHandling_thenResetWithoutStaleEpochRecoveryOrDecryptionError() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Left(NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeSignature))
            .arrange()
        val event = newMLSMessageEvent(TEST_INSTANT)

        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, event, liveDeliveryInfo)

        assertEquals(
            listOf(ResetCall(event.conversationId, arrangement.transactionContext)),
            arrangement.resetCalls
        )
        assertTrue(arrangement.staleEpochCalls.isEmpty())
        verifySuspend(VerifyMode.not) {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenPendingSideEffects_whenFlushing_thenDelegateExactlyOnce() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement().arrange()

        newMessageEventHandler.flushPendingSideEffects()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.applicationMessageHandler.flushPendingSideEffects()
        }
    }

    @Test
    fun givenRemoteRegularMessage_whenHandling_thenLegalHoldPrecedesContentAndConfirmation() = runTest {
        val remoteMessage = applicationMessage.copy(senderUserId = OTHER_USER_ID)
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withProteusUnpackerReturning(Either.Right(remoteMessage))
            .arrange()

        newMessageEventHandler.handleNewProteusMessage(
            arrangement.transactionContext,
            newMessageEvent("encryptedContent"),
            liveDeliveryInfo
        )

        assertEquals(listOf("legalHold", "content", "confirmation"), arrangement.processingOrder)
    }

    @Test
    fun givenSelfAuthoredExpiringMessage_whenHandling_thenLegalHoldPrecedesContentAndSelfDeletion() = runTest {
        val expiringMessage = applicationMessage.copy(
            content = applicationMessage.content.copy(expiresAfterMillis = 123L)
        )
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withProteusUnpackerReturning(Either.Right(expiringMessage))
            .arrange()

        newMessageEventHandler.handleNewProteusMessage(
            arrangement.transactionContext,
            newMessageEvent("encryptedContent"),
            liveDeliveryInfo
        )

        assertEquals(listOf("legalHold", "content", "selfDeletion"), arrangement.processingOrder)
    }

    @Test
    fun givenLegalHoldCallbackFailureResult_whenHandling_thenContinueProcessingMessage() = runTest {
        val remoteMessage = applicationMessage.copy(senderUserId = OTHER_USER_ID)
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withLegalHoldResult(Either.Left(CoreFailure.InvalidEventSenderID))
            .withProteusUnpackerReturning(Either.Right(remoteMessage))
            .arrange()

        newMessageEventHandler.handleNewProteusMessage(
            arrangement.transactionContext,
            newMessageEvent("encryptedContent"),
            liveDeliveryInfo
        )

        assertEquals(listOf("legalHold", "content", "confirmation"), arrangement.processingOrder)
    }

    @Test
    fun givenDependencyException_whenHandling_thenPropagateSameException() = runTest {
        val expected = IllegalStateException("legal-hold failure")
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withLegalHoldException(expected)
            .withProteusUnpackerReturning(Either.Right(applicationMessage))
            .arrange()

        val actual = assertFailsWith<IllegalStateException> {
            newMessageEventHandler.handleNewProteusMessage(
                arrangement.transactionContext,
                newMessageEvent("encryptedContent"),
                liveDeliveryInfo
            )
        }

        assertSame(expected, actual)
        assertEquals(listOf("legalHold"), arrangement.processingOrder)
    }

    @Test
    fun givenDependencyCancellation_whenHandling_thenPropagateSameCancellation() = runTest {
        val expected = CancellationException("content cancelled")
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withContentException(expected)
            .withProteusUnpackerReturning(Either.Right(applicationMessage))
            .arrange()

        val actual = assertFailsWith<CancellationException> {
            newMessageEventHandler.handleNewProteusMessage(
                arrangement.transactionContext,
                newMessageEvent("encryptedContent"),
                liveDeliveryInfo
            )
        }

        assertSame(expected, actual)
        assertEquals(listOf("legalHold", "content"), arrangement.processingOrder)
    }

    private class Arrangement {
        val proteusMessageUnpacker = mock<ProteusMessageUnpacker>()
        val mlsMessageUnpacker = mock<MLSMessageUnpacker>()
        val applicationMessageHandler = mock<ApplicationMessageHandler>(mode = MockMode.autoUnit)
        val proteusContext = mock<ProteusCoreCryptoContext>(mode = MockMode.autoUnit)
        val mlsContext = mock<MlsCoreCryptoContext>(mode = MockMode.autoUnit)
        val transactionContext = mock<CryptoTransactionContext>()

        val legalHoldCalls = mutableListOf<LegalHoldCall>()
        val staleEpochCalls = mutableListOf<StaleEpochCall>()
        val resetCalls = mutableListOf<ResetCall>()
        val selfDeletionCalls = mutableListOf<MessageActionCall>()
        val confirmationDeliveryCalls = mutableListOf<MessageActionCall>()
        val processingOrder = mutableListOf<String>()

        private var legalHoldResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        private var staleEpochResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        private var resetResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        private var legalHoldException: Throwable? = null
        private var contentException: Throwable? = null

        init {
            every { transactionContext.proteus } returns proteusContext
            every { transactionContext.mls } returns mlsContext
            everySuspend {
                applicationMessageHandler.handleContent(any(), any(), any(), any(), any(), any())
            } calls {
                processingOrder += "content"
                contentException?.let { throw it }
                Unit
            }
        }

        private val newMessageEventHandler: NewMessageEventHandler = NewMessageEventHandlerImpl(
            proteusMessageUnpacker = proteusMessageUnpacker,
            mlsMessageUnpacker = mlsMessageUnpacker,
            applicationMessageHandler = applicationMessageHandler,
            handleLegalHoldMessage = { message, isLive ->
                processingOrder += "legalHold"
                legalHoldCalls += LegalHoldCall(message, isLive)
                legalHoldException?.let { throw it }
                legalHoldResult
            },
            enqueueSelfDeletion = { conversationId, messageId ->
                processingOrder += "selfDeletion"
                selfDeletionCalls += MessageActionCall(conversationId, messageId)
            },
            enqueueConfirmationDelivery = { conversationId, messageId ->
                processingOrder += "confirmation"
                confirmationDeliveryCalls += MessageActionCall(conversationId, messageId)
            },
            selfUserId = SELF_USER_ID,
            verifyStaleEpoch = { context, conversationId, subConversationId, timestamp ->
                staleEpochCalls += StaleEpochCall(context, conversationId, subConversationId, timestamp)
                staleEpochResult
            },
            resetMLSConversation = { conversationId, context ->
                resetCalls += ResetCall(conversationId, context)
                resetResult
            },
        )

        suspend fun withProteusUnpackerReturning(result: Either<CoreFailure, MessageUnpackResult.ApplicationMessage>) = apply {
            everySuspend {
                proteusMessageUnpacker.unpackProteusMessage<MessageUnpackResult.ApplicationMessage>(any(), any(), any())
            } calls {
                if (result is Either.Right) {
                    val lambda = it.args[2] as suspend (MessageUnpackResult.ApplicationMessage) -> MessageUnpackResult.ApplicationMessage
                    Either.Right(lambda(result.value))
                } else {
                    result
                }
            }
        }

        suspend fun withHandleLegalHoldSuccess() = apply {
            legalHoldResult = Either.Right(Unit)
        }

        fun withLegalHoldResult(result: Either<CoreFailure, Unit>) = apply {
            legalHoldResult = result
        }

        fun withLegalHoldException(exception: Throwable) = apply {
            legalHoldException = exception
        }

        fun withContentException(exception: Throwable) = apply {
            contentException = exception
        }

        suspend fun withMLSUnpackerReturning(result: Either<CoreFailure, List<MessageUnpackResult>>) =
            apply {
                everySuspend {
                    mlsMessageUnpacker.unpackMlsMessage(any(), any())
                }.returns(result)
            }

        suspend fun withVerifyEpoch(result: Either<CoreFailure, Unit>) = apply {
            staleEpochResult = result
        }

        fun arrange() = this to newMessageEventHandler
    }

    private data class LegalHoldCall(
        val message: MessageUnpackResult.ApplicationMessage,
        val isLive: Boolean,
    )

    private data class StaleEpochCall(
        val transactionContext: CryptoTransactionContext,
        val conversationId: ConversationId,
        val subConversationId: SubconversationId?,
        val timestamp: Instant?,
    )

    private data class ResetCall(
        val conversationId: ConversationId,
        val transactionContext: CryptoTransactionContext,
    )

    private data class MessageActionCall(
        val conversationId: ConversationId,
        val messageId: String,
    )

    private companion object {
        val TEST_INSTANT = Instant.parse("2024-01-02T03:04:05Z")
        val CONVERSATION_ID = ConversationId("conversationId", "conversationDomain")
        val SELF_USER_ID = UserId("selfUserId", "selfDomain")
        val OTHER_USER_ID = UserId("otherUserId", "otherDomain")
        val SENDER_CLIENT_ID = ClientId("senderClientId")
        val liveDeliveryInfo = EventDeliveryInfo(EventSource.LIVE)

        fun newMessageEvent(content: String) = Event.Conversation.NewMessage(
            id = "eventId",
            conversationId = CONVERSATION_ID,
            senderUserId = SELF_USER_ID,
            senderClientId = SENDER_CLIENT_ID,
            messageInstant = TEST_INSTANT,
            content = content,
            encryptedExternalContent = null,
        )

        fun newMLSMessageEvent(
            dateTime: Instant,
            subConversationId: SubconversationId? = null,
        ) = Event.Conversation.NewMLSMessage(
            id = "eventId",
            conversationId = CONVERSATION_ID,
            subconversationId = subConversationId,
            senderUserId = SELF_USER_ID,
            messageInstant = dateTime,
            content = "encryptedContent",
        )

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
