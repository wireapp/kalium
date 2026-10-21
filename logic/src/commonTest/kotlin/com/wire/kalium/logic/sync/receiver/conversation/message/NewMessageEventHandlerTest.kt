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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.sync.incremental.EventSource
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
import com.wire.kalium.logic.data.conversation.ResetMLSConversationResult
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.util.DateTimeUtil
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.MockMode
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class NewMessageEventHandlerTest {

    @Test
    fun committedEffectsStillDecryptProteusWithoutRepeatingApplicationEffects() = runTest {
        val (arrangement, handler) = Arrangement().withProteusUnpackerReturning(Either.Right(applicationMessage))
            .withHandleLegalHoldSuccess().arrange()
        val event = TestEvent.newMessageEvent("encrypted")

        handler.handleNewProteusMessage(
            DurableMLSReceiveContext(arrangement.transactionContext, effectsAlreadyPersisted = true),
            event, TestEvent.liveDeliveryInfo,
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), event, any())
        }
        verifySuspend(VerifyMode.not) { arrangement.applicationMessageHandler.handleContent(any(), any(), any(), any(), any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun proteusReceiptReplayRequeuesLegalHoldContinuation() = runTest {
        val message = applicationMessage.copy(content = applicationMessage.content.copy(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED))
        val (arrangement, handler) = Arrangement().withProteusUnpackerReturning(Either.Right(message))
            .withHandleLegalHoldSuccess().arrange()

        handler.handleNewProteusMessage(
            DurableMLSReceiveContext(arrangement.transactionContext, effectsAlreadyPersisted = true),
            TestEvent.newMessageEvent("encrypted"), EventDeliveryInfo(EventSource.PENDING),
        )

        verifySuspend(VerifyMode.exactly(1)) { arrangement.legalHoldHandler.handleNewMessage(message, false) }
        verifySuspend(VerifyMode.not) { arrangement.applicationMessageHandler.handleContent(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun committedMLSReceiptSkipsUnpackerAndEffects() = runTest {
        val event = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())
        val calls = mutableListOf<Boolean>()
        val (arrangement, handler) = Arrangement { _, received, wrongEpoch ->
            assertEquals(event, received)
            calls += wrongEpoch
            true
        }.arrange()

        handler.handleNewMLSMessage(DurableMLSReceiveContext(arrangement.transactionContext), event, TestEvent.liveDeliveryInfo).shouldSucceed()

        assertEquals(listOf(false), calls)
        verifySuspend(VerifyMode.not) { arrangement.mlsMessageUnpacker.unpackMlsMessage(any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.applicationMessageHandler.handleContent(any(), any(), any(), any(), any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.staleEpochVerifier.verifyEpoch(any(), any(), any(), any()) }
    }

    @Test
    fun wrongEpochRunsRecoveryAfterPreflightAndDoesNotRunLegacyVerifier() = runTest {
        val calls = mutableListOf<Boolean>()
        val (arrangement, handler) = Arrangement { _, _, wrongEpoch ->
            calls += wrongEpoch
            wrongEpoch
        }.withMLSUnpackerReturning(Either.Left(MLSFailure.WrongEpoch)).arrange()

        handler.handleNewMLSMessage(
            DurableMLSReceiveContext(arrangement.transactionContext),
            TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant()),
            TestEvent.liveDeliveryInfo
        ).shouldSucceed()

        assertEquals(listOf(false, true), calls)
        verifySuspend(VerifyMode.not) { arrangement.staleEpochVerifier.verifyEpoch(any(), any(), any(), any()) }
    }

    @Test
    fun recoveryFailureEscapesInsteadOfBeingIgnoredAsADecryptionFailure() = runTest {
        val (arrangement, handler) = Arrangement { _, _, _ -> error("recovery persistence failed") }.arrange()

        assertFailsWith<IllegalStateException> {
            handler.handleNewMLSMessage(
                DurableMLSReceiveContext(arrangement.transactionContext),
                TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant()),
                TestEvent.liveDeliveryInfo
            )
        }
        verifySuspend(VerifyMode.not) { arrangement.mlsMessageUnpacker.unpackMlsMessage(any(), any()) }
    }

    @Test
    fun nonQueueReceiveDoesNotUseDurableRecoveryEvenWithPendingDeliverySource() = runTest {
        val (arrangement, handler) = Arrangement { _, _, _ -> error("Local events must not create recovery intents") }
            .withMLSUnpackerReturning(Either.Left(MLSFailure.WrongEpoch))
            .withVerifyEpoch(Either.Right(Unit))
            .arrange()
        val event = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        handler.handleNewMLSMessage(arrangement.transactionContext, event, EventDeliveryInfo(EventSource.PENDING)).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsMessageUnpacker.unpackMlsMessage(any(), event) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.staleEpochVerifier.verifyEpoch(any(), any(), any(), any()) }
    }

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

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), newMessageEvent, any())
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

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), newMessageEvent, any())
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

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), newMessageEvent, any())
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

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), newMessageEvent, any())
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

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.applicationMessageHandler.handleDecryptionError(
                eventId = newMessageEvent.id,
                conversationId = newMessageEvent.conversationId,
                messageInstant = newMessageEvent.messageInstant,
                senderUserId = newMessageEvent.senderUserId,
                senderClientId = newMessageEvent.senderClientId,
                content = MessageContent.FailedDecryption(
                    encodedData = newMessageEvent.encryptedExternalContent?.data,
                    errorCode = 5,
                    isDecryptionResolved = true,
                    senderUserId = newMessageEvent.senderUserId,
                    clientId = ClientId(newMessageEvent.senderClientId.value)
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

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.applicationMessageHandler.handleDecryptionError(
                eventId = newMessageEvent.id,
                conversationId = newMessageEvent.conversationId,
                messageInstant = newMessageEvent.messageInstant,
                senderUserId = newMessageEvent.senderUserId,
                senderClientId = newMessageEvent.senderClientId,
                content = MessageContent.FailedDecryption(
                    encodedData = newMessageEvent.encryptedExternalContent?.data,
                    errorCode = 2,
                    isDecryptionResolved = false,
                    senderUserId = newMessageEvent.senderUserId,
                    clientId = ClientId(newMessageEvent.senderClientId.value)
                )
            )
        }
    }

    @Test
    fun givenMLSEvent_whenHandling_shouldAskMLSUnpackerToDecrypt() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Right(listOf(MessageUnpackResult.HandshakeMessage)))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        val result = newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsMessageUnpacker.unpackMlsMessage(any(), newMessageEvent)
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

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldHandler.handleNewMessage(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.applicationMessageHandler.handleContent(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenUnpackingSuccess_whenHandling_thenHandleContent() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withMLSUnpackerReturning(Either.Right(listOf(MessageUnpackResult.HandshakeMessage, applicationMessage)))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        val result = newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsMessageUnpacker.unpackMlsMessage(any(), newMessageEvent)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.legalHoldHandler.handleNewMessage(applicationMessage, any())
        }

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

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), newMessageEvent, any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }

        verify(VerifyMode.exactly(1)) {
            arrangement.ephemeralMessageDeletionHandler.startSelfDeletion(any(), any())
        }
    }

    @Test
    fun givenEphemeralMessage_whenHandling_thenDoNotEnqueueForSelfDelete() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(applicationMessage))
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), newMessageEvent, any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }

        verify(VerifyMode.not) {
            arrangement.ephemeralMessageDeletionHandler.startSelfDeletion(any(), any())
        }
    }

    @Test
    fun givenAMessage_whenHandlingSelfMessage_thenEnqueueDeliveryConfirmationShouldNotHappen() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(applicationMessage)).arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")
        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) { arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), newMessageEvent, any()) }
        verifySuspend(VerifyMode.not) { arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.confirmationDeliveryHandler.enqueueConfirmationDelivery(any(), any()) }
    }

    @Test
    fun givenAMessage_whenHandlingSignalingMessage_thenEnqueueDeliveryConfirmationShouldNotHappen() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(signalingMessage))
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")
        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) { arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), newMessageEvent, any()) }
        verifySuspend(VerifyMode.not) { arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.confirmationDeliveryHandler.enqueueConfirmationDelivery(any(), any()) }
    }

    @Test
    fun givenAProteusMessage_whenHandling_thenEnqueueDeliveryConfirmation() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(applicationMessage.copy(senderUserId = TestUser.OTHER_USER_ID_2))).arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")
        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) { arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), newMessageEvent, any()) }
        verifySuspend(VerifyMode.not) { arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.confirmationDeliveryHandler.enqueueConfirmationDelivery(any(), any()) }
    }

    @Test
    fun givenAMLSMessage_whenHandling_thenEnqueueDeliveryConfirmation() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withMLSUnpackerReturning(Either.Right(listOf(applicationMessage.copy(senderUserId = TestUser.OTHER_USER_ID_2))))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())
        newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsMessageUnpacker.unpackMlsMessage(any(), newMessageEvent) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.confirmationDeliveryHandler.enqueueConfirmationDelivery(any(), any()) }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), newMessageEvent, any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldHandler.handleNewMessage(any(), any())
        }
    }

    @Test
    fun givenMessageFromSelf_whenHandling_thenDoNotEnqueueForSelfDelete() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withHandleLegalHoldSuccess()
            .withProteusUnpackerReturning(Either.Right(applicationMessage))
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusMessageUnpacker.unpackProteusMessage<Any>(any(), newMessageEvent, any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.legalHoldHandler.handleNewMessage(applicationMessage, any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
        }

        verify(VerifyMode.not) {
            arrangement.ephemeralMessageDeletionHandler.startSelfDeletion(any(), any())
        }
    }

    @Test
    fun givenMLSEventFailsWithWrongEpoch_whenHandling_shouldCallWrongEpochHandler() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Left(MLSFailure.WrongEpoch))
            .withVerifyEpoch(Either.Right(Unit))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())

        val result = newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.staleEpochVerifier.verifyEpoch(
                any(),
                newMessageEvent.conversationId,
                any(),
                newMessageEvent.messageInstant
            )
        }
    }

    @Test
    fun givenEpochVerifierFails_thenOnlyDurableReceivePropagatesFailure() = runTest {
        val failure = StorageFailure.DataNotFound
        val event = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())
        val (arrangement, handler) = Arrangement()
            .withMLSUnpackerReturning(Either.Left(MLSFailure.WrongEpoch))
            .withVerifyEpoch(Either.Left(failure))
            .arrange()

        handler.handleNewMLSMessage(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo).shouldSucceed()
        handler.handleNewMLSMessage(DurableMLSReceiveContext(arrangement.transactionContext), event, TestEvent.liveDeliveryInfo)
            .shouldFail { assertEquals(failure, it) }
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

            verifySuspend(VerifyMode.not) {
                arrangement.applicationMessageHandler.handleDecryptionError(any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun givenMLSEventFailsWithInvalidLeafNodeIndex_whenHandling_thenResetConversation() = runTest {
        val newMessageEvent = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Left(NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeIndex))
            .withResetConversationReturning(ResetMLSConversationResult.Success)
            .arrange()

        val result = newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, newMessageEvent, TestEvent.liveDeliveryInfo)

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.resetMlsConversation(newMessageEvent.conversationId, arrangement.transactionContext)
        }
    }

    @Test
    fun givenResetConversationFails_thenOnlyDurableReceivePropagatesFailure() = runTest {
        val failure = StorageFailure.DataNotFound
        val event = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())
        val (arrangement, handler) = Arrangement()
            .withMLSUnpackerReturning(Either.Left(NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeIndex))
            .withResetConversationReturning(ResetMLSConversationResult.Failure(failure))
            .arrange()

        handler.handleNewMLSMessage(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo).shouldSucceed()
        handler.handleNewMLSMessage(DurableMLSReceiveContext(arrangement.transactionContext), event, TestEvent.liveDeliveryInfo)
            .shouldFail { assertEquals(failure, it) }
    }

    @Test
    fun givenIgnoredMLSError_whenHandling_thenMessageIsConsumed() = runTest {
        val event = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant())
        val (arrangement, handler) = Arrangement()
            .withMLSUnpackerReturning(Either.Left(MLSFailure.ConversationNotFound))
            .arrange()

        handler.handleNewMLSMessage(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)
            .shouldSucceed()
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

        val result = newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        result.shouldSucceed()

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
        val event = TestEvent.newMLSMessageEvent(dateTime = DateTimeUtil.currentInstant())
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Left(CoreFailure.Unknown(null)))
            .arrange()

        val result = newMessageEventHandler.handleNewMLSMessage(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.applicationMessageHandler.handleDecryptionError(
                eventId = event.id,
                conversationId = event.conversationId,
                messageInstant = event.messageInstant,
                senderUserId = event.senderUserId,
                senderClientId = any(),
                content = matches { it.senderUserId == event.senderUserId && !it.isDecryptionResolved }
            )
        }
    }

    private class Arrangement(private val recovery: MLSReceiveRecoveryHandler? = null): CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val proteusMessageUnpacker = mock<ProteusMessageUnpacker>()
        val mlsMessageUnpacker = mock<MLSMessageUnpacker>()
        val applicationMessageHandler = mock<ApplicationMessageHandler>(mode = MockMode.autoUnit)
        val staleEpochVerifier = mock<StaleEpochVerifier>()
        val ephemeralMessageDeletionHandler = mock<EphemeralMessageDeletionHandler>(mode = MockMode.autoUnit)
        val confirmationDeliveryHandler = mock<ConfirmationDeliveryHandler>(mode = MockMode.autoUnit)
        val legalHoldHandler = mock<LegalHoldHandler>()
        val resetMlsConversation = mock<ResetMLSConversationUseCase>(mode = MockMode.autoUnit)

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
            recovery,
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
            everySuspend {
                legalHoldHandler.handleNewMessage(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withMLSUnpackerReturning(result: Either<CoreFailure, List<MessageUnpackResult>>) =
            apply {
                everySuspend {
                    mlsMessageUnpacker.unpackMlsMessage(any(), any())
                }.returns(result)
            }

        suspend fun withVerifyEpoch(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                staleEpochVerifier.verifyEpoch(any(), any(), any(), any())
            }.returns(result)
        }

        suspend fun withResetConversationReturning(result: ResetMLSConversationResult) = apply {
            everySuspend {
                resetMlsConversation(any(), any())
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
