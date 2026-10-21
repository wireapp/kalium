/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.notification.NotificationMessageKind
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.MessageContentPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NotificationPreviewTest {
    @Test
    fun messageKindFollowsEveryPreviewRestriction() {
        val preferences = MessageContentPreference.entries.map { Either.Right(it) } +
            Either.Left(StorageFailure.Generic(IllegalStateException("unreadable preference")))
        for (kind in NotificationMessageKind.entries) for (showContent in listOf(false, true)) {
            for (eligible in listOf(false, true)) for (previewable in listOf(false, true)) {
                for (preference in preferences) {
                    val original = ReceivedMessageResult(
                        "message", "conversation", "wire.com", null, eligible, previewable,
                        messageKind = kind, senderName = "Sender", conversationName = "Team",
                    )
                    val result = original.notificationPreview(showContent, preference)
                    val allowed = showContent && eligible && previewable && preference is Either.Right &&
                        preference.value != MessageContentPreference.Disabled
                    assertEquals(if (allowed) kind else null, result.messageKind)
                    assertEquals("message", result.messageId)
                    assertEquals(eligible, result.shouldNotify)
                }
            }
        }
    }

    @Test
    fun contentIsVisibleOnlyWhenEveryPolicyAllowsIt() {
        for (requested in listOf(false, true)) {
            for (eligible in listOf(false, true)) {
                for (previewable in listOf(false, true)) {
                    for (preference in MessageContentPreference.entries) {
                        val original = ReceivedMessageResult(
                            "message", "conversation", "wire.com", "secret", eligible, previewable,
                            type = ReceivedNotificationType.CONNECTION_REQUEST,
                            conversationName = "private conversation", senderName = "private sender", isGroupConversation = true,
                        )
                        val result = original.notificationPreview(requested, Either.Right(preference))
                        if (requested && eligible && previewable && preference != MessageContentPreference.Disabled) {
                            assertEquals(original, result)
                        } else {
                            assertNull(result.text)
                            assertNull(result.senderName)
                            assertNull(result.conversationName)
                            assertFalse(result.isGroupConversation)
                            assertFalse(result.mayPreview)
                            assertEquals(ReceivedNotificationType.MESSAGE, result.type)
                            assertEquals(eligible, result.shouldNotify)
                        }
                    }
                }
            }
        }
    }
}
