/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.decryptionFailureContent
import com.wire.kalium.logic.data.message.decryptionFailureMessage
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageFailureHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageFailureResolution

internal fun receivedProteusDecryptionFailure(
    event: Event.Conversation.NewMessage,
    failure: CoreFailure,
    selfUserId: UserId,
): ReceivedPayload.DecryptionFailure {
    // A failed ciphertext can be shown and repaired through the app's explicit reset action.
    // Store/identity/panic failures do not establish that the native account is safe to continue.
    val code = (failure as? ProteusFailure)?.code
    val displayable = when (code) {
        ProteusFailure.Code.REMOTE_IDENTITY_CHANGED, ProteusFailure.Code.INVALID_SIGNATURE,
        ProteusFailure.Code.INVALID_MESSAGE, ProteusFailure.Code.DECODE_ERROR,
        ProteusFailure.Code.SESSION_NOT_FOUND, ProteusFailure.Code.PREKEY_NOT_FOUND,
        ProteusFailure.Code.TOO_DISTANT_FUTURE, ProteusFailure.Code.OUTDATED_MESSAGE -> true
        else -> false
    }
    if (!displayable) throw KaliumSyncException("Native decryption requires recovery before receiving can continue", failure)
    val resolved = ProteusMessageFailureHandler.handleFailure(failure) == ProteusMessageFailureResolution.InformUser
    val message = decryptionFailureMessage(
        eventId = event.id,
        conversationId = event.conversationId,
        messageInstant = event.messageInstant,
        senderUserId = event.senderUserId,
        senderClientId = event.senderClientId,
        content = event.decryptionFailureContent(failure, resolved),
        selfUserId = selfUserId,
    )
    return ReceivedPayload.DecryptionFailure(message)
}
