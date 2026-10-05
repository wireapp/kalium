/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.nse

import com.wire.kalium.logic.sync.receiving.ReceivedMessageResult
import com.wire.kalium.logic.sync.receiving.ReceivedNotificationResult
import com.wire.kalium.logic.sync.receiving.ReceivedNotificationType
import com.wire.kalium.logic.sync.receiving.ReceivedCallResult
import com.wire.kalium.logic.sync.receiving.ReceivedCallType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse

class KaliumNSEPresentationTest {
    private val receiver = KaliumNSEReceiver(KaliumNSEConfiguration(
        rootPath = "unused", keychainService = "unused", keychainAccessGroup = null,
        keychainAccessibility = KaliumNSEKeychainAccessibility.AFTER_FIRST_UNLOCK,
        userAgent = "test", certificatePins = emptyMap(),
    ))

    @Test
    fun systemLabelsRequirePreviewPermission() {
        for (type in ReceivedNotificationType.entries) {
            for (showContent in listOf(false, true)) for (mayPreview in listOf(false, true)) {
                val result = receiver.success(ReceivedNotificationResult(message(type).copy(mayPreview = mayPreview)), showContent)
                if (showContent && mayPreview) {
                    assertEquals(type.name, result.contentType.name)
                    assertEquals("Sender", result.senderName)
                    assertEquals("Group", result.conversationName)
                } else {
                    assertEquals(KaliumNSEContentType.MESSAGE, result.contentType)
                    assertNull(result.text)
                    assertNull(result.senderName)
                    assertNull(result.conversationName)
                    assertFalse(result.isGroupConversation)
                }
            }
        }
    }

    @Test
    fun suppressedSystemEventsRemainSilentAndHaveNoPreview() {
        for (type in ReceivedNotificationType.entries) {
            val result = receiver.success(ReceivedNotificationResult(message(type).copy(shouldNotify = false)), true)
            assertFalse(result.shouldNotify)
            assertEquals(KaliumNSEContentType.MESSAGE, result.contentType)
            assertNull(result.senderName)
            assertNull(result.conversationName)
            assertNull(result.text)
        }
    }

    @Test
    fun missedCallPrivacyDoesNotSuppressEligibleAlertOrConcurrentText() {
        val call = ReceivedCallResult(
            ReceivedCallType.MISSED, "conversation", "example.com", "sender", "example.com", null,
            123, false, false, handoffId = "call", shouldNotify = true,
            senderName = "Sender", conversationName = "Group", isGroupConversation = true,
        )
        for (showContent in listOf(false, true)) {
            val result = receiver.success(ReceivedNotificationResult(message(ReceivedNotificationType.MESSAGE), listOf(call)), showContent)
            assertEquals(true, result.shouldNotify)
            val missed = result.calls.single()
            assertEquals(true, missed.shouldNotify)
            assertEquals(showContent, missed.mayPreview)
            assertEquals(showContent, missed.isGroupConversation)
            assertEquals(if (showContent) "Sender" else null, missed.senderName)
            assertEquals(if (showContent) "Group" else null, missed.conversationName)
            assertEquals("call", missed.handoffId)
        }
    }

    private fun message(type: ReceivedNotificationType) = ReceivedMessageResult(
        "message", "conversation", "example.com", null, true, true, type,
        conversationName = "Group", senderName = "Sender", isGroupConversation = true,
    )
}
