/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.MessageContentPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NotificationPreviewTest {
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
