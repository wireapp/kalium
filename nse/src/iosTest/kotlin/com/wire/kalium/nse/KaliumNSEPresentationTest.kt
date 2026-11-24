/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.nse

import com.wire.kalium.logic.data.notification.NotificationMessageKind
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.MessageContentPreference
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.receiving.AccountRuntimeFailure
import com.wire.kalium.logic.sync.receiving.CatchUpOutcome
import com.wire.kalium.logic.sync.receiving.NotificationAppWork
import com.wire.kalium.logic.sync.receiving.ReceivedMessageResult
import com.wire.kalium.logic.sync.receiving.ReceivedNotificationResult
import com.wire.kalium.logic.sync.receiving.ReceivedNotificationType
import com.wire.kalium.logic.sync.receiving.ReceivedCallResult
import com.wire.kalium.logic.sync.receiving.ReceivedCallType
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.Instant

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
                // As on Android, hidden message content leaves connection requests and conversation deletions intact.
                val showsContent = type != ReceivedNotificationType.MESSAGE || showContent && savedAllowsPreview
                assertEquals(true, result.shouldNotify)
                assertEquals("message", result.messageId)
                assertEquals("conversation", result.conversationValue)
                assertEquals("example.com", result.conversationDomain)
                assertEquals(!showsContent, result.presentationReason == "MESSAGE_CONTENT_HIDDEN")
                if (showsContent && mayPreview) {
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
    fun deliveredAlertEditsFollowCurrentIdentityAndKeepHiddenOrExpiringTextPrivate() {
        val delivered = KaliumNSEDeliveredNotification("alert-id", "conversation", "example.com", "original")
        val edited = storedMessage("edited", MessageEntityContent.Text("Changed text"))
        assertEquals(
            KaliumNSENotificationUpdate("alert-id", "edited", "Changed text"),
            delivered.notificationUpdate("edited", edited, showsMessageContent = true),
        )
        assertEquals(
            KaliumNSENotificationUpdate("alert-id", "edited", null),
            delivered.notificationUpdate("edited", edited, showsMessageContent = false),
        )
        assertEquals(
            KaliumNSENotificationUpdate("alert-id", "edited", null),
            delivered.notificationUpdate("edited", edited.copy(expireAfterMs = 1_000), showsMessageContent = true),
        )
        assertNull(delivered.notificationUpdate("original", edited, showsMessageContent = true))
        assertNull(delivered.notificationUpdate(
            "edited", edited.copy(visibility = MessageEntity.Visibility.DELETED), showsMessageContent = true,
        ))
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
    fun ownershipFailuresAskForAGenericAlertUnlessTheAppHasNotRun() {
        for (reason in AccountRuntimeFailure.entries) {
            val result = receiver.ownershipFailure(reason)
            assertEquals(KaliumNSEStatus.FAILED, result.status)
            assertEquals(reason.name, result.failureReason)
            assertFalse(result.shouldNotify)
            assertEquals(reason != AccountRuntimeFailure.MISSING_STORAGE, result.presentGenericAlert, "$reason")
        }
    }

    @Test
    fun timeoutAsksForAGenericAlertUnlessAnAttemptCaughtUpAndClaimedEverythingAfterThePushArrived() {
        val time = TestTimeSource()
        val accounts = KaliumNSEReceiver.CaughtUpAccounts()
        val user = UserId("user", "example.com")
        val arrived = time.markNow()
        time += 1.seconds
        val complete = ReceivedNotificationResult(null, prefixComplete = true, stoppedReason = "QUEUE_END")
        // Not live, out of time or with a deferred prefix, an attempt may leave the push's event unfetched or unclaimed;
        // a refused or dropped alert was not presented.
        accounts.record("root", user, time.markNow(), CatchUpOutcome.BUDGET, complete, true)
        accounts.record("root", user, time.markNow(), CatchUpOutcome.LIVE, complete.copy(droppedAlert = true), true)
        accounts.record("root", user, time.markNow(), CatchUpOutcome.LIVE, complete.copy(stoppedReason = "BUDGET"), true)
        accounts.record("root", user, time.markNow(), CatchUpOutcome.LIVE, complete.copy(prefixComplete = false), true)
        accounts.record("root", user, time.markNow(), CatchUpOutcome.LIVE, complete, false)
        assertTrue(receiver.timedOut(accounts.handled("root", user, arrived)).presentGenericAlert)

        accounts.record("root", user, time.markNow(), CatchUpOutcome.LIVE, complete, true)
        val handled = receiver.timedOut(accounts.handled("root", user, arrived))
        assertEquals(KaliumNSEStatus.SUCCESS, handled.status)
        assertFalse(handled.presentGenericAlert)
        // That catch-up may have missed a push arriving later, or another account's.
        time += 1.seconds
        assertTrue(receiver.timedOut(accounts.handled("root", user, time.markNow())).presentGenericAlert)
        assertTrue(receiver.timedOut(accounts.handled("root", UserId("other", "example.com"), arrived)).presentGenericAlert)
        assertTrue(receiver.timedOut(accounts.handled("other", user, arrived)).presentGenericAlert)
    }

    @Test
    fun appHandoffAsksForAGenericAlertWhileItsEventWaits() = runTest {
        for (work in listOf(null) + NotificationAppWork.entries) for (targetProcessed in listOf(false, true)) {
            val received = ReceivedNotificationResult(
                null, appWorkPending = work, stoppedReason = if (work == null) "QUEUE_END" else "${work}_REQUIRES_APP"
            )
            val stopsQueue = work == NotificationAppWork.LEGAL_HOLD || work == NotificationAppWork.MLS_SUBCONVERSATION
            val expected = stopsQueue && !targetProcessed
            assertEquals(
                expected,
                receiver.mayLeaveUnseenEvents(CatchUpOutcome.LIVE, received) { targetProcessed },
                "$work $targetProcessed"
            )
        }
        // Running out of time asks too.
        assertTrue(receiver.mayLeaveUnseenEvents(CatchUpOutcome.LIVE, ReceivedNotificationResult(null, stoppedReason = "BUDGET")) {
            false
        })
    }

    private fun unnotified(id: String) = message(ReceivedNotificationType.MESSAGE).copy(messageId = id)

    private fun storedMessage(id: String, content: MessageEntityContent.Regular) = MessageEntity.Regular(
        id = id,
        content = content,
        conversationId = QualifiedIDEntity("conversation", "example.com"),
        date = Instant.fromEpochMilliseconds(1),
        senderUserId = QualifiedIDEntity("sender", "example.com"),
        senderClientId = "client",
        status = MessageEntity.Status.SENT,
        editStatus = MessageEntity.EditStatus.NotEdited,
        readCount = 0,
        senderName = "Sender",
    )

    private fun message(type: ReceivedNotificationType) = ReceivedMessageResult(
        "message", "conversation", "example.com", "Text", true, true, type,
        conversationName = "Group", senderName = "Sender", isGroupConversation = true,
    )
}
