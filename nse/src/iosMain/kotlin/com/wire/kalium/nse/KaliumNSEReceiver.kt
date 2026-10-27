/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.nse

import com.wire.kalium.logic.data.notification.NotificationMessageKind
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.configuration.MessageContentPreference
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.logic.sync.receiving.AppleAccountRuntime
import com.wire.kalium.logic.sync.receiving.CatchUpOutcome
import com.wire.kalium.logic.sync.receiving.catchUp
import com.wire.kalium.logic.sync.receiving.ReceivedCallType
import com.wire.kalium.logic.sync.receiving.ReceivedMessageResult
import com.wire.kalium.logic.sync.receiving.ReceivedNotificationResult
import com.wire.kalium.logic.sync.receiving.ReceivedNotificationType
import com.wire.kalium.logic.sync.receiving.ReceivingDiagnostics
import com.wire.kalium.logic.sync.receiving.ReceivingOptions
import com.wire.kalium.logic.sync.receiving.UnnotifiedMessageAlerts
import com.wire.kalium.logic.sync.receiving.notificationPreview
import com.wire.kalium.logic.sync.receiving.notificationProcessor
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.NotificationMessageKey
import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import com.wire.kalium.persistence.kmmSettings.KeychainAccessibility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Shared configuration must exactly match app preparation, including the canonical App Group root. */
public data class KaliumNSEConfiguration(
    public val rootPath: String,
    public val keychainService: String,
    public val keychainAccessGroup: String?,
    public val keychainAccessibility: KaliumNSEKeychainAccessibility,
    public val userAgent: String,
    public val certificatePins: Map<String, List<String>>,
    /** Must match the app's KaliumConfigs build switches. Server settings remain in account storage. */
    public val guestRoomLink: Boolean = true,
    public val selfDeletingMessages: Boolean = true,
    public val drivePermissionsEnabled: Boolean = false,
    public val linkPreviewEnabled: Boolean = false,
    /** Null allows all file types; a list must match the app's BuildFileRestrictionState.AllowSome. */
    public val allowedFileExtensions: List<String>? = null,
)

public enum class KaliumNSEKeychainAccessibility {
    WHEN_UNLOCKED,
    AFTER_FIRST_UNLOCK,
    AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY,
    WHEN_UNLOCKED_THIS_DEVICE_ONLY,
    WHEN_PASSCODE_SET_THIS_DEVICE_ONLY,
}

public data class KaliumNSERequest(
    public val userValue: String,
    public val userDomain: String,
    public val notificationId: String,
    /** Additional host message-preview restriction; the saved account choice is read for each request. */
    public val showMessageContent: Boolean,
    public val budgetMilliseconds: Long,
    /** The host supplies only delivered message alerts belonging to this request's account. */
    public val deliveredNotifications: List<KaliumNSEDeliveredNotification> = emptyList(),
)

public data class KaliumNSEDeliveredNotification(
    public val identifier: String,
    public val conversationValue: String,
    public val conversationDomain: String,
    public val messageId: String,
)

/** SUCCESS without an alert is distinct from failure. The Swift host owns its single system completion callback. */
public data class KaliumNSEResult(
    public val status: KaliumNSEStatus,
    public val shouldNotify: Boolean,
    public val messageId: String?,
    public val conversationValue: String?,
    public val conversationDomain: String?,
    public val text: String?,
    public val contentType: KaliumNSEContentType = KaliumNSEContentType.MESSAGE,
    public val conversationName: String? = null,
    public val senderName: String? = null,
    /** AVS callbacks for the received prefix, in callback order. Report before completing the NSE request. */
    public val calls: List<KaliumNSECall> = emptyList(),
    /** Diagnostic code only; never contains account data or message content. */
    public val failureReason: String? = null,
    public val presentationReason: String? = null,
    public val callSignalsPending: Boolean = false,
    /** Content-free reason; Events retains the unprocessed suffix for app incremental sync. */
    public val appWorkPending: String? = null,
    /** Actual UNNotificationRequest identifiers, reconciled after successful receiving. */
    public val notificationIdentifiersToRemove: List<String> = emptyList(),
    public val isGroupConversation: Boolean = false,
    /**
     * Further unnotified message alerts, oldest first. Post each before completing the request;
     * Kalium has already marked them notified.
     */
    public val alerts: List<KaliumNSEAlert> = emptyList(),
    /**
     * Receiving ran out of time or failed to catch up before reaching the request's notification, and has
     * no alert to show. The host shows a generic alert instead of empty content. Also set on
     * [KaliumNSEStatus.FAILED] timeouts.
     */
    public val presentGenericAlert: Boolean = false,
    /** Diagnostic code only: why receiving stopped, or `CATCH_UP_*` when the catch-up did not reach live. */
    public val stoppedReason: String? = null,
    public val messageKind: NotificationMessageKind? = null,
    /** Absolute unread-conversation count after receiving; absent when receiving failed. */
    public val unreadConversationBadgeCount: Long? = null,
)

/** A message alert beyond the request's own; text and names are present only when preview is allowed. */
public data class KaliumNSEAlert(
    public val messageId: String,
    public val conversationValue: String,
    public val conversationDomain: String,
    public val text: String?,
    public val conversationName: String?,
    public val senderName: String?,
    public val isGroupConversation: Boolean,
    public val messageKind: NotificationMessageKind? = null,
)

public enum class KaliumNSEStatus { SUCCESS, FAILED }
public enum class KaliumNSEContentType { MESSAGE, CONVERSATION_DELETED, CONNECTION_REQUEST }

public enum class KaliumNSECallType { INCOMING, MISSED, CLOSED }

/** Call presentation only; the host owns CallKit and its account identifier. */
public data class KaliumNSECall(
    public val type: KaliumNSECallType,
    public val conversationValue: String,
    public val conversationDomain: String,
    public val callerValue: String,
    public val callerDomain: String,
    public val callerClientId: String?,
    public val messageTimeSeconds: Long,
    public val shouldRing: Boolean,
    public val isVideoCall: Boolean,
    public val closeReason: Int?,
    /** Upstream call policy may suppress names; message-preview preferences never change this value. */
    public val callerName: String?,
    public val handoffId: String? = null,
    /** Eligible, first presentation of a missed call; never controls incoming CallKit delivery. */
    public val shouldNotify: Boolean = false,
    public val mayPreview: Boolean = false,
    public val conversationName: String? = null,
    public val senderName: String? = null,
    public val isGroupConversation: Boolean = false,
)

/**
 * Enqueue using only the supplied alert and native notification APIs, never account resources.
 * Cancellation may release the account before this callback completes; late acceptance is replayed
 * through stable notification identities rather than acknowledging against a closed database.
 */
public interface KaliumNSEMessageDelivery {
    public suspend fun deliver(alert: KaliumNSEAlert): Boolean
}

/** Bounded prepared-account receiving. Unsupported earlier work stops the attempt and preserves its processed marker. */
public class KaliumNSEReceiver(private val configuration: KaliumNSEConfiguration) {
    @Throws(CancellationException::class)
    public suspend fun process(request: KaliumNSERequest): KaliumNSEResult = processOwned(request, null)

    @Throws(CancellationException::class)
    public suspend fun processAndDeliver(request: KaliumNSERequest, delivery: KaliumNSEMessageDelivery): KaliumNSEResult =
        processOwned(request, delivery)

    private suspend fun processOwned(request: KaliumNSERequest, delivery: KaliumNSEMessageDelivery?): KaliumNSEResult {
        val diagnostics = ReceivingDiagnostics()
        if (request.budgetMilliseconds !in 1..MAX_BUDGET_MS || request.notificationId.isBlank()) {
            return failure("INVALID_REQUEST").also { diagnostics.record("FAILED", it.failureReason, false) }
        }
        val started = TimeSource.Monotonic.markNow()
        // A result produced before the budget ends survives a slow runtime close: its message alerts are marked notified.
        var produced: KaliumNSEResult? = null
        val outcome = try {
            withTimeoutOrNull(request.budgetMilliseconds) {
                val result = AppleAccountRuntime.use(
                    configuration.rootPath,
                    UserId(request.userValue, request.userDomain),
                    ApplePersistenceConfig(
                        serviceName = configuration.keychainService,
                        keychainAccessibility = configuration.keychainAccessibility.toApple(),
                        accessGroup = configuration.keychainAccessGroup,
                    ),
                    configuration.userAgent,
                    configuration.certificatePins,
                    diagnostics = diagnostics,
                ) { runtime ->
                    // The badge is auxiliary: a slow count read must not discard already-produced alerts.
                    val received = receive(runtime, request, diagnostics, started, delivery).also { produced = it }
                    received.copy(
                        unreadConversationBadgeCount = runtime.readUnreadConversationBadgeCount().getOrNull()
                    ).also { produced = it }
                }
                when (result) {
                    is Either.Left -> failure(result.value.name)
                    is Either.Right -> result.value
                }
            } ?: produced ?: failure("TIMEOUT").copy(presentGenericAlert = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") cleanup: Exception) {
            // Receiving finished and marked its message alerts notified; only closing the runtime failed.
            produced ?: throw cleanup
        }
        diagnostics.record(outcome.status.name, outcome.failureReason, outcome.shouldNotify)
        return outcome
    }

    private suspend fun receive(
        runtime: AppleAccountRuntime,
        request: KaliumNSERequest,
        diagnostics: ReceivingDiagnostics,
        started: TimeSource.Monotonic.ValueTimeMark,
        delivery: KaliumNSEMessageDelivery?,
    ): KaliumNSEResult {
        runtime.database.messageDAO.rememberNotificationMessages(request.deliveredNotifications.map { it.messageKey() })
        val processor = runtime.notificationProcessor(
            ReceivingOptions(
                guestRoomLink = configuration.guestRoomLink,
                selfDeletingMessages = configuration.selfDeletingMessages,
                drivePermissionsEnabled = configuration.drivePermissionsEnabled,
                linkPreviewEnabled = configuration.linkPreviewEnabled,
                fileRestrictionState = configuration.allowedFileExtensions?.let {
                    BuildFileRestrictionState.AllowSome(it)
                } ?: BuildFileRestrictionState.NoRestriction
            ),
            diagnostics = diagnostics,
        )
        val budget = request.budgetMilliseconds.milliseconds
        // As the app does, catch up over the WebSocket, then process the stored queue to its end. Soft deadlines
        // leave time to present and to close the runtime within the budget.
        val caughtUp = runtime.catchUp(budget * CATCH_UP_SHARE - started.elapsedNow())
        val received = processor.processToLive(request.notificationId) { started.elapsedNow() > budget * PROCESS_SHARE }.let {
            if (caughtUp == CatchUpOutcome.LIVE || it.stoppedReason != QUEUE_END) it
            else it.copy(stoppedReason = "CATCH_UP_${caughtUp.name}")
        }
        val claims = UnnotifiedMessageAlerts(runtime.database, runtime.prepared.userId)
        claims.pending.acknowledge(request.deliveredNotifications.map { it.messageKey() })
        if (received.prefixComplete) claims.take()
        val unnotified = claims.pending.read(request.showMessageContent)
        val result = reconcileNotifications(runtime, received, unnotified, request)
        if (delivery != null) deliverMessageAlerts(result.alerts, delivery) { key ->
            claims.pending.acknowledge(listOf(key))
        }
        return result.copy(
            stoppedReason = received.stoppedReason,
            presentGenericAlert = !result.shouldNotify && result.alerts.isEmpty() &&
                mayLeaveUnseenEvents(runtime, caughtUp, received, request.notificationId),
        )
    }

    /**
     * An unfinished catch-up may leave events the socket received, which get no push; missed notifications
     * mean the backend dropped events until the app's slow sync. A failed catch-up, like processing that ran
     * out of time, may not have reached the push's own event.
     */
    private suspend fun mayLeaveUnseenEvents(
        runtime: AppleAccountRuntime,
        caughtUp: CatchUpOutcome,
        received: ReceivedNotificationResult,
        notificationId: String,
    ): Boolean = caughtUp == CatchUpOutcome.BUDGET || caughtUp == CatchUpOutcome.NEEDS_SLOW_SYNC || (
        (received.stoppedReason == BUDGET || caughtUp == CatchUpOutcome.FAILED) &&
            runtime.database.eventDAO.getEventById(notificationId)?.isProcessed != true
        )

    private suspend fun reconcileNotifications(
        runtime: AppleAccountRuntime,
        processed: ReceivedNotificationResult,
        unnotified: List<ReceivedMessageResult>,
        request: KaliumNSERequest,
    ): KaliumNSEResult {
        val (received, extras) = presentation(processed, unnotified)
        val preference = runtime.readMessageContentPreference()
        val result = success(received, request.showMessageContent, preference).copy(
            alerts = unnotified.map { alert(it, request.showMessageContent, preference) }
        )
        val alerted = extras + listOfNotNull(received.messageAlert())
        runtime.database.messageDAO.rememberNotificationMessages(alerted.map { it.messageKey() })
        val removals = runtime.database.messageDAO.observeNotificationRemovalCandidates(
            request.deliveredNotifications.map { it.messageId }.distinct()
        ).first().toSet()
        return result.copy(
            notificationIdentifiersToRemove = request.deliveredNotifications.filter {
                it.messageKey() in removals
            }.map { it.identifier }
        )
    }

    private fun KaliumNSEDeliveredNotification.messageKey() = NotificationMessageKey(
        messageId,
        QualifiedIDEntity(conversationValue, conversationDomain)
    )

    private fun ReceivedMessageResult.messageKey() = NotificationMessageKey(
        messageId,
        QualifiedIDEntity(conversationValue, conversationDomain)
    )

    /** The request's own message alert, registered for later removal and never repeated among unnotified messages. */
    private fun ReceivedNotificationResult.messageAlert() =
        message?.takeIf { it.shouldNotify && it.type == ReceivedNotificationType.MESSAGE }

    /**
     * The request keeps its own alert and the host posts the other unnotified messages. A request without
     * its own alert, such as one whose event an earlier attempt processed, shows the newest instead.
     */
    internal fun presentation(
        received: ReceivedNotificationResult,
        unnotified: List<ReceivedMessageResult>,
    ): Pair<ReceivedNotificationResult, List<ReceivedMessageResult>> {
        val ownKey = received.messageAlert()?.messageKey()
        val others = unnotified.filterNot { it.messageKey() == ownKey }
        return if (received.message?.shouldNotify == true || others.isEmpty()) received to others
        else received.copy(message = others.last(), presentationReason = null) to others.dropLast(1)
    }

    /** An unnotified message alert, with the request's preview rules. */
    internal fun alert(
        message: ReceivedMessageResult,
        showContent: Boolean,
        preference: Either<StorageFailure, MessageContentPreference>,
    ): KaliumNSEAlert {
        val preview = success(ReceivedNotificationResult(message), showContent, preference)
        return KaliumNSEAlert(
            message.messageId,
            message.conversationValue,
            message.conversationDomain,
            preview.text,
            preview.conversationName,
            preview.senderName,
            preview.isGroupConversation,
            preview.messageKind,
        )
    }

    @Suppress("CyclomaticComplexMethod") // Maps optional receiving fields to the public result.
    internal fun success(
        result: ReceivedNotificationResult,
        showContent: Boolean,
        preference: Either<StorageFailure, MessageContentPreference>,
    ): KaliumNSEResult {
        val message = result.message
        val preview = message?.notificationPreview(showContent, preference)
        val contentType = when (preview?.type) {
            ReceivedNotificationType.CONVERSATION_DELETED -> KaliumNSEContentType.CONVERSATION_DELETED
            ReceivedNotificationType.CONNECTION_REQUEST -> KaliumNSEContentType.CONNECTION_REQUEST
            ReceivedNotificationType.MESSAGE, null -> KaliumNSEContentType.MESSAGE
        }
        return KaliumNSEResult(
            KaliumNSEStatus.SUCCESS, message?.shouldNotify == true, message?.messageId,
            message?.conversationValue, message?.conversationDomain, preview?.text,
            messageKind = preview?.messageKind,
            callSignalsPending = result.callSignalsPending,
            appWorkPending = result.appWorkPending?.name,
            presentationReason = result.presentationReason ?: when {
                message == null -> "NO_MESSAGE_CANDIDATE"
                !message.shouldNotify -> message.suppressionReason ?: "NOT_ELIGIBLE"
                // The preview says why it hides content, so only messages report hidden message content.
                preview?.mayPreview == false -> preview.previewPolicyReason ?: "PREVIEW_POLICY"
                message.messageKind != null && message.messageKind != NotificationMessageKind.TEXT -> "TYPED_MESSAGE"
                message.text.isNullOrEmpty() -> "NO_TEXT"
                else -> "TEXT"
            },
            // A typed system label is also preview content, even when its text is null.
            contentType = contentType,
            conversationName = preview?.conversationName,
            senderName = preview?.senderName,
            isGroupConversation = preview?.isGroupConversation == true,
            calls = result.calls.map { call ->
                KaliumNSECall(
                    type = when (call.type) {
                        ReceivedCallType.INCOMING -> KaliumNSECallType.INCOMING
                        ReceivedCallType.MISSED -> KaliumNSECallType.MISSED
                        ReceivedCallType.CLOSED -> KaliumNSECallType.CLOSED
                    },
                    conversationValue = call.conversationValue,
                    conversationDomain = call.conversationDomain,
                    callerValue = call.callerValue,
                    callerDomain = call.callerDomain,
                    callerClientId = call.callerClientId,
                    messageTimeSeconds = call.messageTimeSeconds,
                    shouldRing = call.shouldRing,
                    isVideoCall = call.isVideoCall,
                    closeReason = call.closeReason,
                    callerName = call.callerName,
                    handoffId = call.handoffId,
                    shouldNotify = call.shouldNotify,
                    mayPreview = call.shouldNotify,
                    conversationName = call.conversationName?.takeIf { call.shouldNotify },
                    senderName = call.senderName?.takeIf { call.shouldNotify },
                    isGroupConversation = call.shouldNotify && call.isGroupConversation,
                )
            },
        )
    }

    private fun failure(reason: String) = KaliumNSEResult(
        KaliumNSEStatus.FAILED,
        false,
        null,
        null,
        null,
        null,
        failureReason = reason
    )

    private companion object {
        const val MAX_BUDGET_MS = 25_000L
        const val CATCH_UP_SHARE = 0.6
        const val PROCESS_SHARE = 0.85
        const val QUEUE_END = "QUEUE_END"
        const val BUDGET = "BUDGET"
    }
}

private fun KaliumNSEKeychainAccessibility.toApple(): KeychainAccessibility = when (this) {
    KaliumNSEKeychainAccessibility.WHEN_UNLOCKED -> KeychainAccessibility.WhenUnlocked
    KaliumNSEKeychainAccessibility.WHEN_UNLOCKED_THIS_DEVICE_ONLY -> KeychainAccessibility.WhenUnlockedThisDeviceOnly
    KaliumNSEKeychainAccessibility.AFTER_FIRST_UNLOCK -> KeychainAccessibility.AfterFirstUnlock
    KaliumNSEKeychainAccessibility.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY -> KeychainAccessibility.AfterFirstUnlockThisDeviceOnly
    KaliumNSEKeychainAccessibility.WHEN_PASSCODE_SET_THIS_DEVICE_ONLY -> KeychainAccessibility.WhenPasscodeSetThisDeviceOnly
}
