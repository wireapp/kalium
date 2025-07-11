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

package com.wire.kalium.logic.feature.message.receipt

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.util.serialization.toJsonElement
import io.mockative.Mockable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * This use case allows to send a confirmation type [ReceiptType.READ]
 *
 * - For 1:1 we take into consideration [UserPropertyRepository.getReadReceiptsStatus]
 * - For group conversations we have to look for each group conversation configuration.
 */
@Mockable
internal interface SendConfirmationUseCase {
    suspend operator fun invoke(
        conversationId: ConversationId,
        afterDateTime: Instant,
        untilDateTime: Instant
    ): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList", "FunctionNaming")
internal fun SendConfirmationUseCase(
    currentClientIdProvider: CurrentClientIdProvider,
    syncManager: SyncManager,
    messageSender: MessageSender,
    selfUserId: UserId,
    conversationRepository: ConversationRepository,
    messageRepository: MessageRepository,
    userPropertyRepository: UserPropertyRepository,
) = object : SendConfirmationUseCase {
    val TAG = "SendConfirmation"
    val conversationIdKey = "conversationId"
    val messageIdsKey = "messageIds"
    val statusKey = "status"

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.MESSAGES) }

    override suspend operator fun invoke(
        conversationId: ConversationId,
        afterDateTime: Instant,
        untilDateTime: Instant
    ): Either<CoreFailure, Unit> {
        syncManager.waitUntilLive()

        val messageIds = getPendingUnreadMessagesIds(
            conversationId,
            afterDateTime,
            untilDateTime
        )
        if (messageIds.isEmpty()) {
            logConfirmationNothingToSend(conversationId)
            return Either.Right(Unit)
        }

        return currentClientIdProvider().flatMap { currentClientId ->
            val message = Message.Signaling(
                id = uuid4().toString(),
                content = MessageContent.Receipt(ReceiptType.READ, messageIds),
                conversationId = conversationId,
                date = Clock.System.now(),
                senderUserId = selfUserId,
                senderClientId = currentClientId,
                status = Message.Status.Pending,
                isSelfMessage = true,
                expirationData = null
            )

            messageSender.sendMessage(message, messageTarget = MessageTarget.Conversation(setOf(selfUserId)))
        }.onFailure {
            logConfirmationError(conversationId, messageIds, it)
        }.onSuccess {
            logConfirmationSuccess(conversationId, messageIds)
        }
    }

    private suspend fun getPendingUnreadMessagesIds(
        conversationId: ConversationId,
        afterDateTime: Instant,
        untilDateTime: Instant
    ): List<String> =
        if (isReceiptsEnabledForConversation(conversationId)) {
            messageRepository.getPendingConfirmationMessagesByConversationAfterDate(
                conversationId,
                afterDateTime,
                untilDateTime,
            ).fold({
                logger.e("$TAG There was an unknown error trying to get messages pending read confirmation $it")
                emptyList()
            }, { it })
        } else emptyList()

    private suspend fun isReceiptsEnabledForConversation(conversationId: ConversationId) =
        conversationRepository.getConversationById(conversationId).fold({
            false
        }, { conversation ->
            when (conversation.type) {
                Conversation.Type.OneOnOne -> userPropertyRepository.getReadReceiptsStatus()
                else -> conversation.receiptMode == Conversation.ReceiptMode.ENABLED
            }
        })

    private fun logConfirmationNothingToSend(conversationId: ConversationId) {
        val properties = mapOf(
            conversationIdKey to conversationId.toLogString(),
            statusKey to "NOTHING_TO_CONFIRM"
        )
        val jsonLogString = Json.encodeToString(properties.toMap())
        val logMessage = "$TAG: $jsonLogString"

        logger.d(logMessage)
    }

    private fun logConfirmationError(conversationId: ConversationId, messageIds: List<String>, failure: CoreFailure) {

        val properties = mapOf(
            conversationIdKey to conversationId.toLogString(),
            messageIdsKey to messageIds.map { it.obfuscateId() },
            statusKey to "ERROR",
            "errorInfo" to "$failure"
        )

        val jsonLogString = "${properties.toJsonElement()}"
        val logMessage = "$TAG: $jsonLogString"

        logger.e(logMessage)
    }

    private fun logConfirmationSuccess(conversationId: ConversationId, messageIds: List<String>) {

        val properties = mapOf(
            conversationIdKey to conversationId.toLogString(),
            messageIdsKey to messageIds.map { it.obfuscateId() },
            statusKey to "CONFIRMED"
        )
        val jsonLogString = "${properties.toJsonElement()}"
        val logMessage = "$TAG: $jsonLogString"

        logger.i(logMessage)
    }
}
