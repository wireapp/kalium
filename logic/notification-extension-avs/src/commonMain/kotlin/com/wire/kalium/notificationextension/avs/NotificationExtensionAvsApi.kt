/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.notificationextension.avs

import com.wire.kalium.calling.notifications.AvsCallNotification
import com.wire.kalium.calling.notifications.AvsCallNotificationCallbacks
import com.wire.kalium.calling.notifications.AvsCallNotificationClosedReason
import com.wire.kalium.calling.notifications.AvsCallNotificationConversationType
import com.wire.kalium.calling.notifications.AvsCallNotificationNativeOperation
import com.wire.kalium.calling.notifications.AvsCallNotificationProcessingFailure
import com.wire.kalium.calling.notifications.AvsCallNotificationProcessingResult
import com.wire.kalium.calling.notifications.AvsCallNotificationProcessor
import com.wire.kalium.calling.notifications.AvsCallNotificationProcessorFactory
import com.wire.kalium.calling.notifications.AvsClosedCallNotification
import com.wire.kalium.calling.notifications.AvsIncomingCallNotification
import com.wire.kalium.calling.notifications.AvsMissedCallNotification

/** Scalar/string-only input constructed independently by Swift in the AVS dynamic framework. */
@Suppress("LongParameterList")
public data class NotificationExtensionAvsEvent(
    public val payload: String,
    public val currentTimeSeconds: Long,
    public val messageTimeSeconds: Long,
    public val conversationId: String,
    public val senderUserId: String,
    public val senderClientId: String,
    public val conversationType: NotificationExtensionAvsConversationType
) {
    override fun toString(): String = "NotificationExtensionAvsEvent(redacted)"
}

public enum class NotificationExtensionAvsConversationType {
    ONE_ON_ONE,
    GROUP,
    CONFERENCE,
    CONFERENCE_MLS,
    UNKNOWN
}

public data class NotificationExtensionAvsIncomingCall(
    public val conversationId: String,
    public val messageTimeSeconds: Long,
    public val callerUserId: String,
    public val callerClientId: String?,
    public val isVideoCall: Boolean,
    public val shouldRing: Boolean,
    public val conversationType: NotificationExtensionAvsConversationType
) {
    override fun toString(): String = "NotificationExtensionAvsIncomingCall(redacted)"
}

public data class NotificationExtensionAvsMissedCall(
    public val conversationId: String,
    public val messageTimeSeconds: Long,
    public val callerUserId: String,
    public val isVideoCall: Boolean
) {
    override fun toString(): String = "NotificationExtensionAvsMissedCall(redacted)"
}

public data class NotificationExtensionAvsClosedCall(
    public val reasonCode: Int,
    public val conversationId: String,
    public val messageTimeSeconds: Long,
    public val callerUserId: String,
    public val callerClientId: String?
) {
    override fun toString(): String = "NotificationExtensionAvsClosedCall(redacted)"
}

public interface NotificationExtensionAvsCallbacks {
    public fun onIncomingCall(incomingCall: NotificationExtensionAvsIncomingCall)
    public fun onMissedCall(missedCall: NotificationExtensionAvsMissedCall)
    public fun onClosedCall(closedCall: NotificationExtensionAvsClosedCall)
}

public enum class NotificationExtensionAvsStatus {
    SUCCESS,
    INVALID_INPUT,
    UNSUPPORTED_PLATFORM,
    NATIVE_FAILURE
}

public enum class NotificationExtensionAvsOperation {
    NONE,
    CREATE,
    START,
    PROCESS,
    END,
    CLOSE
}

/** Concrete, payload-free AVS result. Native error text is intentionally not exported. */
public data class NotificationExtensionAvsResult(
    public val status: NotificationExtensionAvsStatus,
    public val operation: NotificationExtensionAvsOperation,
    public val nativeCode: Int?,
    public val eventIndex: Int?
)

/**
 * Notification-only AVS façade for a separate dynamic framework.
 *
 * Swift must call this synchronously from the core framework's call bridge while the core M5 lease
 * is still held. One processor is created late and closed exactly once for each non-empty valid
 * batch. The full `:domain:calling` module is not a dependency.
 */
public class NotificationExtensionAvsProcessor {
    public fun process(
        selfUserId: String,
        selfClientId: String,
        events: List<NotificationExtensionAvsEvent>,
        callbacks: NotificationExtensionAvsCallbacks
    ): NotificationExtensionAvsResult {
        if (!inputIsValid(selfUserId, selfClientId, events)) return invalidInput()

        var processor: AvsCallNotificationProcessor? = null
        val processingResult = try {
            processor = AvsCallNotificationProcessorFactory.create(
                selfUserId = selfUserId,
                selfClientId = selfClientId,
                callbacks = AvsCallbacksAdapter(callbacks)
            )
            processor.process(events.map(::toDomainEvent)).toExtensionResult()
        } catch (_: Throwable) {
            nativeFailure(NotificationExtensionAvsOperation.CREATE)
        }

        val closeSucceeded = processor?.let { runCatching { it.close() }.isSuccess } ?: true
        return if (closeSucceeded) processingResult else nativeFailure(NotificationExtensionAvsOperation.CLOSE)
    }
}

private class AvsCallbacksAdapter(
    private val callbacks: NotificationExtensionAvsCallbacks
) : AvsCallNotificationCallbacks {
    override fun onIncomingCall(incomingCall: AvsIncomingCallNotification) {
        runCatching {
            callbacks.onIncomingCall(
                NotificationExtensionAvsIncomingCall(
                    conversationId = incomingCall.conversationId,
                    messageTimeSeconds = incomingCall.messageTimeSeconds.toLong(),
                    callerUserId = incomingCall.callerUserId,
                    callerClientId = incomingCall.callerClientId,
                    isVideoCall = incomingCall.isVideoCall,
                    shouldRing = incomingCall.shouldRing,
                    conversationType = incomingCall.conversationType.toExtensionType()
                )
            )
        }
    }

    override fun onMissedCall(missedCall: AvsMissedCallNotification) {
        runCatching {
            callbacks.onMissedCall(
                NotificationExtensionAvsMissedCall(
                    conversationId = missedCall.conversationId,
                    messageTimeSeconds = missedCall.messageTimeSeconds.toLong(),
                    callerUserId = missedCall.callerUserId,
                    isVideoCall = missedCall.isVideoCall
                )
            )
        }
    }

    override fun onClosedCall(closedCall: AvsClosedCallNotification) {
        runCatching {
            callbacks.onClosedCall(
                NotificationExtensionAvsClosedCall(
                    reasonCode = closedCall.reason.toReasonCode(),
                    conversationId = closedCall.conversationId,
                    messageTimeSeconds = closedCall.messageTimeSeconds.toLong(),
                    callerUserId = closedCall.callerUserId,
                    callerClientId = closedCall.callerClientId
                )
            )
        }
    }
}

private fun inputIsValid(
    selfUserId: String,
    selfClientId: String,
    events: List<NotificationExtensionAvsEvent>
): Boolean = selfUserId.isNotBlank() && selfClientId.isNotBlank() && events.size in 1..MAX_EVENTS_PER_BATCH &&
        events.all { event ->
            event.payload.isNotEmpty() && event.conversationId.isNotBlank() && event.senderUserId.isNotBlank() &&
                    event.senderClientId.isNotBlank() && event.currentTimeSeconds in UINT_RANGE &&
                    event.messageTimeSeconds in UINT_RANGE
        }

private fun toDomainEvent(event: NotificationExtensionAvsEvent): AvsCallNotification = AvsCallNotification(
    payload = event.payload.encodeToByteArray(),
    currentTimeSeconds = event.currentTimeSeconds.toUInt(),
    messageTimeSeconds = event.messageTimeSeconds.toUInt(),
    conversationId = event.conversationId,
    senderUserId = event.senderUserId,
    senderClientId = event.senderClientId,
    conversationType = event.conversationType.toDomainType()
)

private fun NotificationExtensionAvsConversationType.toDomainType(): AvsCallNotificationConversationType = when (this) {
    NotificationExtensionAvsConversationType.ONE_ON_ONE -> AvsCallNotificationConversationType.OneOnOne
    NotificationExtensionAvsConversationType.GROUP -> AvsCallNotificationConversationType.Group
    NotificationExtensionAvsConversationType.CONFERENCE -> AvsCallNotificationConversationType.Conference
    NotificationExtensionAvsConversationType.CONFERENCE_MLS -> AvsCallNotificationConversationType.ConferenceMls
    NotificationExtensionAvsConversationType.UNKNOWN -> AvsCallNotificationConversationType.Unknown
}

private fun AvsCallNotificationConversationType.toExtensionType(): NotificationExtensionAvsConversationType = when (this) {
    AvsCallNotificationConversationType.OneOnOne -> NotificationExtensionAvsConversationType.ONE_ON_ONE
    AvsCallNotificationConversationType.Group -> NotificationExtensionAvsConversationType.GROUP
    AvsCallNotificationConversationType.Conference -> NotificationExtensionAvsConversationType.CONFERENCE
    AvsCallNotificationConversationType.ConferenceMls -> NotificationExtensionAvsConversationType.CONFERENCE_MLS
    else -> NotificationExtensionAvsConversationType.UNKNOWN
}

private fun AvsCallNotificationProcessingResult.toExtensionResult(): NotificationExtensionAvsResult = when (this) {
    AvsCallNotificationProcessingResult.Success -> NotificationExtensionAvsResult(
        NotificationExtensionAvsStatus.SUCCESS,
        NotificationExtensionAvsOperation.NONE,
        null,
        null
    )
    is AvsCallNotificationProcessingResult.Failure -> reason.toExtensionResult()
}

private fun AvsCallNotificationProcessingFailure.toExtensionResult(): NotificationExtensionAvsResult = when (this) {
    AvsCallNotificationProcessingFailure.UnsupportedPlatform -> NotificationExtensionAvsResult(
        NotificationExtensionAvsStatus.UNSUPPORTED_PLATFORM,
        NotificationExtensionAvsOperation.NONE,
        null,
        null
    )
    AvsCallNotificationProcessingFailure.EmptyEvents,
    is AvsCallNotificationProcessingFailure.EmptyPayload -> invalidInput()
    is AvsCallNotificationProcessingFailure.NativeFailure -> NotificationExtensionAvsResult(
        status = NotificationExtensionAvsStatus.NATIVE_FAILURE,
        operation = operation.toExtensionOperation(),
        nativeCode = code,
        eventIndex = eventIndex
    )
}

private fun AvsCallNotificationNativeOperation.toExtensionOperation(): NotificationExtensionAvsOperation = when (this) {
    AvsCallNotificationNativeOperation.Create -> NotificationExtensionAvsOperation.CREATE
    AvsCallNotificationNativeOperation.Start -> NotificationExtensionAvsOperation.START
    AvsCallNotificationNativeOperation.Process -> NotificationExtensionAvsOperation.PROCESS
    AvsCallNotificationNativeOperation.End -> NotificationExtensionAvsOperation.END
}

@Suppress("MagicNumber", "CyclomaticComplexMethod")
private fun AvsCallNotificationClosedReason.toReasonCode(): Int = when (this) {
    AvsCallNotificationClosedReason.Normal -> 0
    AvsCallNotificationClosedReason.Error -> 1
    AvsCallNotificationClosedReason.Timeout -> 2
    AvsCallNotificationClosedReason.LostMedia -> 3
    AvsCallNotificationClosedReason.Cancelled -> 4
    AvsCallNotificationClosedReason.AnsweredElsewhere -> 5
    AvsCallNotificationClosedReason.IoError -> 6
    AvsCallNotificationClosedReason.StillOngoing -> 7
    AvsCallNotificationClosedReason.TimeoutConnection -> 8
    AvsCallNotificationClosedReason.DataChannel -> 9
    AvsCallNotificationClosedReason.Rejected -> 10
    AvsCallNotificationClosedReason.OutdatedClient -> 11
    AvsCallNotificationClosedReason.NoOneJoined -> 12
    AvsCallNotificationClosedReason.EveryoneLeft -> 13
    else -> -1
}

private fun invalidInput(): NotificationExtensionAvsResult = NotificationExtensionAvsResult(
    NotificationExtensionAvsStatus.INVALID_INPUT,
    NotificationExtensionAvsOperation.NONE,
    null,
    null
)

private fun nativeFailure(operation: NotificationExtensionAvsOperation): NotificationExtensionAvsResult =
    NotificationExtensionAvsResult(NotificationExtensionAvsStatus.NATIVE_FAILURE, operation, null, null)

private const val MAX_EVENTS_PER_BATCH = 32
private val UINT_RANGE = 0L..UInt.MAX_VALUE.toLong()
