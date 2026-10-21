/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.MessageContentPreference

/** Shared fail-closed preview policy for the extension and the application's existing account owner. */
fun ReceivedMessageResult.notificationPreview(
    showContent: Boolean,
    preference: Either<StorageFailure, MessageContentPreference>,
): ReceivedMessageResult {
    val allowsPreview = preference is Either.Right && preference.value != MessageContentPreference.Disabled
    val messageAllowsPreview = shouldNotify && mayPreview
    if (showContent && allowsPreview && messageAllowsPreview) return this
    return copy(
        text = null,
        conversationName = null,
        senderName = null,
        isGroupConversation = false,
        type = ReceivedNotificationType.MESSAGE,
        mayPreview = false,
        previewPolicyReason = if (!showContent || !allowsPreview) "MESSAGE_CONTENT_HIDDEN" else previewPolicyReason,
    )
}
