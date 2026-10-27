/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlin.time.Instant

fun Event.Conversation.NewMessage.decryptionFailureContent(failure: CoreFailure, resolved: Boolean): MessageContent.FailedDecryption =
    MessageContent.FailedDecryption(
        encodedData = encryptedExternalContent?.data,
        errorCode = (failure as? ProteusFailure)?.intCode,
        isDecryptionResolved = resolved,
        senderUserId = senderUserId,
        clientId = ClientId(senderClientId.value),
    )

/** Failed ciphertext uses the backend event identity and does not enqueue a delivery confirmation. */
@Suppress("LongParameterList")
fun decryptionFailureMessage(
    eventId: String,
    conversationId: ConversationId,
    messageInstant: Instant,
    senderUserId: UserId,
    senderClientId: ClientId,
    content: MessageContent.FailedDecryption,
    selfUserId: UserId,
): Message.Regular = Message.Regular(
    id = eventId,
    content = content,
    conversationId = conversationId,
    date = messageInstant,
    senderUserId = senderUserId,
    senderClientId = senderClientId,
    status = Message.Status.Sent,
    editStatus = Message.EditStatus.NotEdited,
    visibility = Message.Visibility.VISIBLE,
    isSelfMessage = senderUserId == selfUserId,
)
