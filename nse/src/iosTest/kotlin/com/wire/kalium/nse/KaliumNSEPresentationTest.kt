/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.nse

import com.wire.kalium.logic.data.notification.NotificationMessageKind
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.MessageContentPreference
import com.wire.kalium.logic.sync.receiving.AccountRuntimeFailure
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
    private val preferences: List<Either<StorageFailure, MessageContentPreference>> =
        MessageContentPreference.entries.map { Either.Right(it) } + Either.Left(StorageFailure.Generic(IllegalStateException("read failed")))

    @Test
    fun typedPreviewsFollowPrivacyForPrimaryAndAdditionalAlerts() {
        for (kind in NotificationMessageKind.entries) {
            for (showContent in listOf(false, true)) for (mayPreview in listOf(false, true))
                for (shouldNotify in listOf(false, true)) for (preference in preferences) {
                    val message = message(ReceivedNotificationType.MESSAGE).copy(
                        text = null, messageKind = kind, mayPreview = mayPreview, shouldNotify = shouldNotify,
                    )
                    val expected = kind.takeIf {
                        showContent && mayPreview && shouldNotify &&
                            (preference == Either.Right(MessageContentPreference.Unset) ||
                                preference == Either.Right(MessageContentPreference.Enabled))
                    }
                    val result = receiver.success(ReceivedNotificationResult(message), showContent, preference)
                    val additional = receiver.alert(message, showContent, preference)
                    assertEquals(expected, result.messageKind)
                    assertEquals(expected, additional.messageKind)
                    assertEquals(shouldNotify, result.shouldNotify)
                    assertEquals("message", result.messageId)
                    assertEquals(if (expected != null) "Sender" else null, result.senderName)
                    assertEquals(result.senderName, additional.senderName)
                }
        }
    }

    @Test
    fun savedChoiceNeverOverridesHostOrMessagePolicyAndPreservesRouting() {
        for (type in ReceivedNotificationType.entries) {
            for (showContent in listOf(false, true)) for (mayPreview in listOf(false, true)) for (preference in preferences) {
                val result = receiver.success(
                    ReceivedNotificationResult(message(type).copy(mayPreview = mayPreview)), showContent, preference,
                )
                val savedAllowsPreview = preference == Either.Right(MessageContentPreference.Unset) ||
                    preference == Either.Right(MessageContentPreference.Enabled)
                assertEquals(true, result.shouldNotify)
                assertEquals("message", result.messageId)
                assertEquals("conversation", result.conversationValue)
                assertEquals("example.com", result.conversationDomain)
                if (showContent && mayPreview && savedAllowsPreview) {
                    assertEquals(type.name, result.contentType.name)
                    assertEquals("Sender", result.senderName)
                    assertEquals("Group", result.conversationName)
                    assertEquals("Text", result.text)
                    assertEquals(true, result.isGroupConversation)
                } else {
                    assertEquals(KaliumNSEContentType.MESSAGE, result.contentType)
                    assertNull(result.text)
                    assertNull(result.senderName)
                    assertNull(result.conversationName)
                    assertFalse(result.isGroupConversation)
                }
                // Unnotified message alerts follow the same rules.
                if (type == ReceivedNotificationType.MESSAGE) assertEquals(
                    KaliumNSEAlert(
                        "message", "conversation", "example.com",
                        result.text, result.conversationName, result.senderName, result.isGroupConversation,
                    ),
                    receiver.alert(message(type).copy(mayPreview = mayPreview), showContent, preference),
                )
            }
        }
    }

    @Test
    fun suppressedSystemEventsRemainSilentAndHaveNoPreview() {
        for (type in ReceivedNotificationType.entries) for (preference in preferences) {
            val result = receiver.success(ReceivedNotificationResult(message(type).copy(shouldNotify = false)), true, preference)
            assertFalse(result.shouldNotify)
            assertEquals(KaliumNSEContentType.MESSAGE, result.contentType)
            assertNull(result.senderName)
            assertNull(result.conversationName)
            assertNull(result.text)
        }
    }

    @Test
    fun messagePreferencesAndReadFailuresNeverChangeCallPresentation() {
        for (type in ReceivedCallType.entries) for (shouldNotify in listOf(false, true)) for (name in listOf(null, "Caller")) {
            val call = ReceivedCallResult(
                type, "conversation", "example.com", "sender", "example.com", "client",
                123, true, true, closeReason = 7, callerName = name, handoffId = "call", shouldNotify = shouldNotify,
                senderName = "Sender", conversationName = "Group", isGroupConversation = true,
            )
            val received = ReceivedNotificationResult(message(ReceivedNotificationType.MESSAGE), listOf(call))
            val expected = KaliumNSECall(
                KaliumNSECallType.valueOf(type.name), "conversation", "example.com", "sender", "example.com", "client",
                123, true, true, 7, name, "call", shouldNotify, shouldNotify,
                "Group".takeIf { shouldNotify }, "Sender".takeIf { shouldNotify }, shouldNotify,
            )
            for (showContent in listOf(false, true)) for (preference in preferences) {
                val result = receiver.success(received, showContent, preference)
                assertEquals(KaliumNSEStatus.SUCCESS, result.status)
                assertEquals(expected, result.calls.single())
            }
        }
    }

    @Test
    fun requestKeepsItsOwnAlertAndTheHostPostsTheOtherUnnotifiedMessages() {
        for (type in ReceivedNotificationType.entries) {
            val own = ReceivedNotificationResult(message(type))
            val (presented, others) = receiver.presentation(own, listOf(unnotified("earlier"), unnotified("message")))
            assertEquals(own, presented)
            // Only a message alert is the unnotified message with its ID.
            val expected = if (type == ReceivedNotificationType.MESSAGE) listOf("earlier") else listOf("earlier", "message")
            assertEquals(expected, others.map { it.messageId })
        }
    }

    @Test
    fun requestWithoutItsOwnAlertShowsTheNewestUnnotifiedMessage() {
        val processed = ReceivedNotificationResult(null, presentationReason = "ALREADY_PROCESSED")
        val (presented, others) = receiver.presentation(processed, listOf(unnotified("first"), unnotified("second")))
        assertEquals("second", presented.message?.messageId)
        assertNull(presented.presentationReason)
        assertEquals(listOf("first"), others.map { it.messageId })
        assertEquals(processed to emptyList(), receiver.presentation(processed, emptyList()))

        val read = ReceivedNotificationResult(message(ReceivedNotificationType.MESSAGE).copy(shouldNotify = false))
        assertEquals("earlier", receiver.presentation(read, listOf(unnotified("earlier"))).first.message?.messageId)
    }

    @Test
    fun ownershipFailuresAskForAGenericAlertOnlyWhenNobodyElsePresents() {
        for (reason in AccountRuntimeFailure.entries) for (appPresentsAlerts in listOf(false, true)) {
            val result = receiver.ownershipFailure(reason, appPresentsAlerts)
            val expected = when (reason) {
                AccountRuntimeFailure.BUSY, AccountRuntimeFailure.NOT_PREPARED, AccountRuntimeFailure.INCOMPATIBLE ->
                    !appPresentsAlerts
                AccountRuntimeFailure.PROTECTED_STORAGE, AccountRuntimeFailure.STORAGE -> true
                AccountRuntimeFailure.MISSING_STORAGE -> false
            }
            assertEquals(KaliumNSEStatus.FAILED, result.status)
            assertEquals(reason.name, result.failureReason)
            assertFalse(result.shouldNotify)
            assertEquals(expected, result.presentGenericAlert, "$reason $appPresentsAlerts")
        }
    }

    private fun unnotified(id: String) = message(ReceivedNotificationType.MESSAGE).copy(messageId = id)

    private fun message(type: ReceivedNotificationType) = ReceivedMessageResult(
        "message", "conversation", "example.com", "Text", true, true, type,
        conversationName = "Group", senderName = "Sender", isGroupConversation = true,
    )
}
