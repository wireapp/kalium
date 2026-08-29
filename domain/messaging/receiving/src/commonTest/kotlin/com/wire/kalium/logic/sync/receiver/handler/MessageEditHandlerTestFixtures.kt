/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEditPersistence
import com.wire.kalium.logic.data.message.MessageEditState
import com.wire.kalium.logic.data.notification.EphemeralConversationNotification
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Instant

internal class RecordingMessageEditPersistence(
    var loadResult: Either<StorageFailure, MessageEditState>,
    val events: MutableList<String> = mutableListOf(),
) : MessageEditPersistence {
    var textResult: Either<CoreFailure, Unit> = Either.Right(Unit)
    var multipartResult: Either<CoreFailure, Unit> = Either.Right(Unit)
    var statusResult: Either<CoreFailure, Unit> = Either.Right(Unit)
    var throwableOperation: String? = null
    var throwable: Throwable? = null

    val loadCalls = mutableListOf<Pair<ConversationId, String>>()
    val textCalls = mutableListOf<EditCall<MessageContent.TextEdited>>()
    val multipartCalls = mutableListOf<EditCall<MessageContent.MultipartEdited>>()
    val statusCalls = mutableListOf<Pair<ConversationId, String>>()

    override suspend fun loadMessageEditState(
        conversationId: ConversationId,
        messageId: String,
    ): Either<StorageFailure, MessageEditState> {
        events += LOAD
        loadCalls += conversationId to messageId
        throwIfConfigured(LOAD)
        return loadResult
    }

    override suspend fun applyTextEdit(
        conversationId: ConversationId,
        messageContent: MessageContent.TextEdited,
        newMessageId: String,
        editInstant: Instant,
    ): Either<CoreFailure, Unit> {
        events += UPDATE
        textCalls += EditCall(conversationId, messageContent, newMessageId, editInstant)
        throwIfConfigured(UPDATE)
        return textResult
    }

    override suspend fun applyMultipartEdit(
        conversationId: ConversationId,
        messageContent: MessageContent.MultipartEdited,
        newMessageId: String,
        editInstant: Instant,
    ): Either<CoreFailure, Unit> {
        events += UPDATE
        multipartCalls += EditCall(conversationId, messageContent, newMessageId, editInstant)
        throwIfConfigured(UPDATE)
        return multipartResult
    }

    override suspend fun markMessageAsSent(
        conversationId: ConversationId,
        messageId: String,
    ): Either<CoreFailure, Unit> {
        events += STATUS
        statusCalls += conversationId to messageId
        throwIfConfigured(STATUS)
        return statusResult
    }

    private fun throwIfConfigured(operation: String) {
        if (throwableOperation == operation) {
            throw requireNotNull(throwable)
        }
    }

    internal data class EditCall<T>(
        val conversationId: ConversationId,
        val content: T,
        val newMessageId: String,
        val editInstant: Instant,
    )

    internal companion object {
        const val LOAD = "load"
        const val UPDATE = "update"
        const val STATUS = "status"
    }
}

internal class RecordingEditNotificationManager(
    private val events: MutableList<String>,
) : NotificationEventsManager {
    var throwable: Throwable? = null
    val textCalls = mutableListOf<Pair<Message, MessageContent.TextEdited>>()
    val multipartCalls = mutableListOf<Pair<Message, MessageContent.MultipartEdited>>()

    override suspend fun scheduleEditMessageNotification(
        message: Message,
        messageContent: MessageContent.TextEdited,
    ) {
        events += NOTIFY
        textCalls += message to messageContent
        throwable?.let { throw it }
    }

    override suspend fun scheduleEditMessageNotification(
        message: Message,
        messageContent: MessageContent.MultipartEdited,
    ) {
        events += NOTIFY
        multipartCalls += message to messageContent
        throwable?.let { throw it }
    }

    override suspend fun observeEphemeralNotifications(): Flow<LocalNotification> = emptyFlow()

    override suspend fun scheduleDeleteConversationNotification(
        ephemeralConversationNotification: EphemeralConversationNotification,
    ) = Unit

    override suspend fun scheduleDeleteMessageNotification(message: Message) = Unit

    override suspend fun scheduleConversationSeenNotification(conversationId: ConversationId) = Unit

    override suspend fun scheduleRegularNotificationChecking() = Unit

    override suspend fun observeRegularNotificationsChecking(): Flow<Unit> = emptyFlow()

    internal companion object {
        const val NOTIFY = "notify"
    }
}

internal fun storageFailure(message: String): StorageFailure = StorageFailure.Generic(IllegalStateException(message))

internal val otherContentState = MessageEditState(
    senderUserId = UserId("sender", "wire.example"),
    content = MessageEditState.Content.Other,
)

internal val envelopeConversationId = ConversationId("conversation", "wire.example")
internal val originalSenderId = UserId("sender", "wire.example")
internal val otherSenderId = UserId("other-sender", "wire.example")
internal val incomingEditInstant = Instant.parse("2026-08-19T10:15:30Z")
internal const val originalMessageId = "original-message-id"
internal const val incomingMessageId = "incoming-message-id"

internal fun signalingMessage(content: MessageContent.Signaling) = Message.Signaling(
    id = incomingMessageId,
    content = content,
    conversationId = envelopeConversationId,
    date = incomingEditInstant,
    senderUserId = originalSenderId,
    senderClientId = com.wire.kalium.logic.data.conversation.ClientId("sender-client"),
    status = Message.Status.Sent,
    isSelfMessage = false,
    expirationData = null,
)
