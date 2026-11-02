/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.MessageContentPreference

/**
 * Shared fail-closed preview policy for the extension and the application's existing account owner. As on
 * Android, hidden message content hides messages only: connection requests and conversation deletions keep
 * their names and labels.
 */
fun ReceivedMessageResult.notificationPreview(
    showContent: Boolean,
    preference: Either<StorageFailure, MessageContentPreference>,
): ReceivedMessageResult {
    val allowsPreview = preference is Either.Right && preference.value != MessageContentPreference.Disabled
    val showsContent = type != ReceivedNotificationType.MESSAGE || showContent && allowsPreview
    val messageAllowsPreview = shouldNotify && mayPreview
    if (showsContent && messageAllowsPreview) return this
    return copy(
        text = null,
        messageKind = null,
        conversationName = null,
        senderName = null,
        isGroupConversation = false,
        type = ReceivedNotificationType.MESSAGE,
        mayPreview = false,
        previewPolicyReason = if (!showsContent) "MESSAGE_CONTENT_HIDDEN" else previewPolicyReason,
    )
}
