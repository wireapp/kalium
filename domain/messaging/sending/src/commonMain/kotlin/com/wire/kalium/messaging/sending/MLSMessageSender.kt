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

package com.wire.kalium.messaging.sending

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapNetworkMlsFailureIfApplicable
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.MESSAGES
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageSent
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import kotlin.time.Instant

@Suppress("LongParameterList")
internal class MLSMessageSender(
    private val messages: OutgoingMessages,
    private val transport: MessageTransport,
    private val session: SendingSession,
    private val mlsMessageCreator: MLSMessageCreator,
    private val verifyEpoch: suspend (CryptoTransactionContext, ConversationId) -> Either<CoreFailure, Unit>,
    private val handleGroupOutOfSync: suspend (
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        groupId: GroupID,
        failure: NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync
    ) -> Either<CoreFailure, Unit>,
    private val targetedAndTransientMLSMessagesEnabled: Boolean
) {

    private val logger get() = kaliumLogger.withFeatureId(MESSAGES)

    /**
     * Attempts to send an MLS application message, or a targeted message when [messageTarget] addresses specific
     * users or clients. Either one is transient when [transient] is true.
     *
     * [resend] resolves the current conversation protocol again after a repair or when falling back.
     */
    suspend fun send(
        protocolInfo: Conversation.ProtocolInfo.MLS,
        message: Message.Sendable,
        messageTarget: MessageTarget,
        transient: Boolean,
        resend: suspend (MessageTarget, Boolean) -> Either<CoreFailure, Instant>
    ): Either<CoreFailure, Instant> {
        if (
            message.content is MessageContent.Calling &&
            protocolInfo.groupState == Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_AFTER_RESET
        ) {
            logger.i("Skipping calling message while MLS reset is pending")
            return Either.Right(message.date)
        }

        return session.encryptionTransaction("encryptMlsMessage", message) { transactionContext ->
            createOutgoingMLSMessage(transactionContext, protocolInfo.groupId, message, messageTarget, transient)
        }
            .flatMap { sendMLSMessage ->
                sendMLSMessage().fold({
                    handleTargetedTransientMessagesNotSupported(it, resend)
                        .flatMapLeft {
                            handleMlsRejection(it, protocolInfo, message, messageTarget, transient, resend)
                        }
                }, { messageSent ->
                    handleMlsRecipientsDeliveryFailure(message, messageSent).flatMap {
                        Either.Right(messageSent.time)
                    }
                })
            }
            .onFailure {
                if (it is StaleUntilLive || it === NotLive) return@onFailure
                logger.logStructuredJson(
                    level = KaliumLogLevel.ERROR,
                    leadingMessage = "Message Send Failure",
                    jsonStringKeyValues = mapOf(
                        "message" to message.toLogString(),
                        "protocolInfo" to protocolInfo.toLogMap(),
                        "protocol" to CreateConversationParam.Protocol.MLS.name,
                        "errorInfo" to "$it"
                    )
                )
            }.onSuccess {
                logger.logStructuredJson(
                    level = KaliumLogLevel.INFO,
                    leadingMessage = "Message Send Success",
                    jsonStringKeyValues = mapOf(
                        "message" to message.toLogString(),
                        "protocolInfo" to protocolInfo.toLogMap(),
                        "protocol" to CreateConversationParam.Protocol.MLS.name,
                    )
                )
            }
    }

    private suspend fun handleTargetedTransientMessagesNotSupported(
        failure: CoreFailure,
        resend: suspend (MessageTarget, Boolean) -> Either<CoreFailure, Instant>
    ): Either<CoreFailure, Instant> =
        if (failure is NetworkFailure.FeatureNotSupported) {
            logger.w(
                "Targeted & transient MLS messages is not supported by the backend, " +
                        "sending a persistent message to the whole group"
            )
            resend(MessageTarget.Conversation(), false)
        } else {
            Either.Left(failure)
        }

    /**
     * Recovers from a rejected MLS send. On "mls-stale-message" it verifies the epoch and returns [StaleUntilLive],
     * so the caller can recheck the stored message and retry once sync is live.
     */
    private suspend fun handleMlsRejection(
        failure: CoreFailure,
        protocolInfo: Conversation.ProtocolInfo.MLS,
        message: Message.Sendable,
        messageTarget: MessageTarget,
        transient: Boolean,
        resend: suspend (MessageTarget, Boolean) -> Either<CoreFailure, Instant>
    ): Either<CoreFailure, Instant> =
        when (val mlsRejectionCause = (failure.wrapNetworkMlsFailureIfApplicable() as? MLSFailure.MessageRejected)?.cause) {
            is NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync -> {
                session.transaction("handleGroupOutOfSync") { transactionContext ->
                    handleGroupOutOfSync(
                        transactionContext,
                        message.conversationId,
                        protocolInfo.groupId,
                        mlsRejectionCause
                    )
                }.flatMap { resend(messageTarget, transient) }
            }
            is NetworkFailure.MlsMessageRejectedFailure.StaleMessage -> {
                logger.logStructuredJson(
                    level = KaliumLogLevel.WARN,
                    leadingMessage = "Message Send Stale",
                    jsonStringKeyValues = mapOf(
                        "message" to message.toLogString(),
                        "protocolInfo" to protocolInfo.toLogMap(),
                        "protocol" to CreateConversationParam.Protocol.MLS.name,
                        "errorInfo" to "$failure"
                    )
                )
                val stale = StaleUntilLive(failure)
                session.transaction("verifyEpoch") { transactionContext ->
                    verifyEpoch(transactionContext, message.conversationId)
                }.flatMap { Either.Left(stale) }
            }
            else -> Either.Left(failure)
        }

    /**
     * Encrypts [message] for [messageTarget] and returns the request that sends it.
     * A targeted message that selects no group member is not sent. A transient or targeted message is sent as a
     * persistent application message to the whole group instead when [targetedAndTransientMLSMessagesEnabled] is
     * false or the backend doesn't support it.
     */
    private suspend fun createOutgoingMLSMessage(
        transactionContext: CryptoTransactionContext,
        groupId: GroupID,
        message: Message.Sendable,
        messageTarget: MessageTarget,
        transient: Boolean
    ): Either<CoreFailure, suspend () -> Either<CoreFailure, MessageSent>> {
        val isTargeted = targetedAndTransientMLSMessagesEnabled &&
            (messageTarget !is MessageTarget.Conversation || messageTarget.usersToIgnore.isNotEmpty())
        val isTransient = targetedAndTransientMLSMessagesEnabled && transient
        val outgoingMessage = when {
            !isTargeted && !isTransient ->
                return mlsMessageCreator.prepareMLSGroupAndCreateOutgoingMLSMessage(transactionContext, groupId, message)
                    .map { mlsMessage -> { transport.sendMLSMessage(mlsMessage) } }

            !isTargeted ->
                mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTransientMLSMessage(transactionContext, groupId, message)
                    .map { mlsMessage -> suspend { transport.sendMLSTransientMessage(mlsMessage) } }

            else ->
                mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(
                    transactionContext,
                    groupId,
                    message,
                    messageTarget,
                    isTransient
                ).map { batch -> sendTargetedBatch(batch, isTransient, message) }
        }
        return outgoingMessage
    }

    private fun sendTargetedBatch(
        batch: MLSMessageApi.TargetedMessageBatch,
        transient: Boolean,
        message: Message.Sendable
    ): suspend () -> Either<CoreFailure, MessageSent> = {
        when {
            batch.value.isEmpty() -> Either.Right(MessageSent(time = message.date))
            transient -> transport.sendMLSTransientTargetedMessage(batch)
            else -> transport.sendMLSTargetedMessage(batch)
        }
    }

    private suspend fun handleMlsRecipientsDeliveryFailure(message: Message, messageSent: MessageSent) =
        if (messageSent.failedToConfirmClients.isEmpty()) Either.Right(Unit)
        else {
            messages.persistRecipientsDeliveryFailure(message.conversationId, message.id, messageSent.failedToConfirmClients)
        }

    /** A stale MLS send whose epoch was verified and can be retried once sync is live. */
    class StaleUntilLive(val cause: CoreFailure) : CoreFailure.FeatureFailure()
}
