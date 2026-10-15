/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.calling.notifications.AvsCallNotification
import com.wire.kalium.calling.notifications.AvsCallNotificationCallbacks
import com.wire.kalium.calling.notifications.AvsCallNotificationConversationType
import com.wire.kalium.calling.notifications.AvsCallNotificationProcessingResult
import com.wire.kalium.calling.notifications.AvsCallNotificationProcessorFactory
import com.wire.kalium.calling.notifications.AvsClosedCallNotification
import com.wire.kalium.calling.notifications.AvsIncomingCallNotification
import com.wire.kalium.calling.notifications.AvsMissedCallNotification
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.AppleCallTrace
import com.wire.kalium.logger.CallTraceContext
import com.wire.kalium.logger.CallTraceEvent
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Clock
import kotlin.time.Instant

private const val MILLISECONDS_PER_SECOND = 1000L

@Suppress("LongMethod", "CyclomaticComplexMethod") // Native callback collection and presentation eligibility.
internal suspend fun evaluateCallingEvents(
    events: List<ReceivedCallEvent>,
    self: UserId,
    clientId: String,
    database: UserDatabaseBuilder,
): List<ReceivedCallResult> {
    if (events.isEmpty()) return emptyList()
    currentCoroutineContext().ensureActive()
    val signalJournal = CallSignalJournal(database)
    val journal = signalJournal.signals()
    val messageIds = events.map { it.messageId }.toSet()
    journal.filter { it.messageId in messageIds }.forEach {
        AppleCallTrace.record(CallTraceEvent.DURABLE_READY, CallTraceContext(signal = it.id))
    }
    val results = mutableListOf<ReceivedCallResult>()
    fun record(
        type: ReceivedCallType,
        conversationId: String,
        callerId: String,
        callerClientId: String?,
        time: UInt,
        ring: Boolean = false,
        video: Boolean = false,
        reason: Int? = null,
    ) {
        val event = events.lastOrNull {
            it.conversationId.avsId() == conversationId && it.senderId.avsId() == callerId && it.time.epochSeconds == time.toLong()
        } ?: return
        results += ReceivedCallResult(
            type, event.conversationId.value, event.conversationId.domain,
            event.senderId.value, event.senderId.domain, callerClientId,
            time.toLong(), ring, video, reason,
        )
    }
    val callbacks = object : AvsCallNotificationCallbacks {
        override fun onIncomingCall(incomingCall: AvsIncomingCallNotification) = with(incomingCall) {
            record(ReceivedCallType.INCOMING, conversationId, callerUserId, callerClientId, messageTimeSeconds, shouldRing, isVideoCall)
        }

        override fun onMissedCall(missedCall: AvsMissedCallNotification) = with(missedCall) {
            record(ReceivedCallType.MISSED, conversationId, callerUserId, null, messageTimeSeconds, video = isVideoCall)
        }

        override fun onClosedCall(closedCall: AvsClosedCallNotification) = with(closedCall) {
            record(ReceivedCallType.CLOSED, conversationId, callerUserId, callerClientId, messageTimeSeconds, reason = reason.value)
        }
    }
    val processor = AvsCallNotificationProcessorFactory.create(self.toDao().avsId(), clientId, callbacks)
    try {
        val now = Clock.System.now().epochSeconds.toUInt()
        val notifications = events.map {
            val type = when {
                !it.isGroup -> AvsCallNotificationConversationType.OneOnOne
                it.isMLS -> AvsCallNotificationConversationType.ConferenceMls
                else -> AvsCallNotificationConversationType.Group
            }
            AvsCallNotification(
                payload = it.payload.encodeToByteArray(),
                currentTimeSeconds = now,
                messageTimeSeconds = it.time.epochSeconds.toUInt(),
                conversationId = it.conversationId.avsId(),
                senderUserId = it.senderId.avsId(),
                senderClientId = it.senderClientId,
                conversationType = type,
            )
        }
        val result = processor.process(notifications)
        // Like the iOS NSE, call presentation is best effort after sync. AVS rejects expired
        // or invalid call payloads while evaluating the remaining events and final callbacks.
        if (result is AvsCallNotificationProcessingResult.Failure) {
            kaliumLogger.w("NSE AVS call batch reported a processing failure")
        }
    } finally {
        processor.close()
    }
    currentCoroutineContext().ensureActive()
    val availability = database.userDAO.getUserDetailsByQualifiedID(self.toDao())?.availabilityStatus
    // Android timestamps the missed-call message when the outcome is handled, not when ringing started.
    val notificationTime = Clock.System.now()
    return results.map { result ->
        val conversation = database.conversationDAO.getConversationById(
            QualifiedIDEntity(result.conversationValue, result.conversationDomain)
        )
        val canPreview = conversation != null &&
            conversation.mlsVerificationStatus != ConversationEntity.VerificationStatus.DEGRADED &&
            conversation.proteusVerificationStatus != ConversationEntity.VerificationStatus.DEGRADED
        val name = if (!canPreview) null else if (conversation.type.isGroup) conversation.name else {
            database.userDAO.getNameAndHandle(QualifiedIDEntity(result.callerValue, result.callerDomain))?.name ?: conversation.name
        }
        // Missed callbacks do not include a client or signaling message ID. Pin them to the
        // first matching row in the full journal, so pending-batch narrowing cannot move identity.
        val source = if (result.type == ReceivedCallType.INCOMING) signalJournal.incomingSource(
            IncomingCallReference(
                QualifiedIDEntity(result.conversationValue, result.conversationDomain),
                QualifiedIDEntity(result.callerValue, result.callerDomain),
                result.callerClientId,
                result.messageTimeSeconds,
            )
        ) else callSignalSource(journal, messageIds, result)
        val missedClaimed = result.type == ReceivedCallType.MISSED && source != null &&
            CallSignalJournal(database).claimMissedNotification(source.id)
        val shouldNotify = missedClaimed && missedCallShouldNotify(
            conversation = conversation,
            self = self,
            caller = QualifiedIDEntity(result.callerValue, result.callerDomain),
            notificationTime = notificationTime,
            availability = availability,
        )
        val senderName = if (result.type == ReceivedCallType.MISSED) {
            database.userDAO.getNameAndHandle(QualifiedIDEntity(result.callerValue, result.callerDomain))?.name
        } else null
        // AVS can mark our other device's start as ringing. Keep its signaling for host replay,
        // but never present it as an incoming call on this account.
        val shouldRing = result.shouldRing &&
            QualifiedIDEntity(result.callerValue, result.callerDomain) != self.toDao() &&
            availability != UserAvailabilityStatusEntity.AWAY && conversation != null &&
            conversation.mutedStatus != ConversationEntity.MutedStatus.ALL_MUTED
        platform.Foundation.NSLog(
            "CALL_FLOW call=${source?.id ?: "none"} stage=nse_decision type=${result.type} " +
                "ring=$shouldRing notify=$shouldNotify"
        )
        val event = when (result.type) {
            ReceivedCallType.INCOMING -> CallTraceEvent.NSE_INCOMING
            ReceivedCallType.CLOSED -> CallTraceEvent.NSE_CLOSED
            ReceivedCallType.MISSED -> CallTraceEvent.NSE_MISSED
        }
        AppleCallTrace.record(
            event,
            CallTraceContext(signal = source?.id),
            code = result.closeReason,
            flag = shouldRing
        )
        result.copy(
            callerName = if (result.type == ReceivedCallType.MISSED) null else name,
            handoffId = source?.id,
            shouldRing = shouldRing,
            shouldNotify = shouldNotify,
            conversationName = if (result.type == ReceivedCallType.MISSED) conversation?.name else null,
            senderName = senderName,
            isGroupConversation = result.type == ReceivedCallType.MISSED && conversation?.type?.isGroup == true,
        )
    }
}

internal fun callSignalSource(
    journal: List<CallSignalRecord>,
    pendingMessageIds: Set<String>,
    result: ReceivedCallResult,
): CallSignalRecord? {
    val matchesIdentity: (CallSignalRecord) -> Boolean = {
        it.conversationValue == result.conversationValue && it.conversationDomain == result.conversationDomain &&
            it.senderValue == result.callerValue && it.senderDomain == result.callerDomain &&
            it.timeMillis / MILLISECONDS_PER_SECOND == result.messageTimeSeconds
    }
    return if (result.type == ReceivedCallType.MISSED) {
        journal.firstOrNull(matchesIdentity)
    } else {
        journal.lastOrNull {
            it.messageId in pendingMessageIds &&
                (result.callerClientId == null || it.senderClientId == result.callerClientId) &&
                matchesIdentity(it)
        }
    }
}

internal fun missedCallShouldNotify(
    conversation: ConversationEntity?,
    self: UserId,
    caller: QualifiedIDEntity,
    notificationTime: Instant,
    availability: UserAvailabilityStatusEntity?,
): Boolean = conversation != null &&
    !conversation.archived &&
    caller != self.toDao() &&
    notificationTime > conversation.lastReadDate &&
    conversation.lastNotificationDate?.let { notificationTime > it } != false &&
    when (conversation.mutedStatus) {
        ConversationEntity.MutedStatus.ALL_MUTED,
        ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED -> false
        ConversationEntity.MutedStatus.ALL_ALLOWED ->
            availability !in setOf(UserAvailabilityStatusEntity.BUSY, UserAvailabilityStatusEntity.AWAY)
        ConversationEntity.MutedStatus.MENTIONS_MUTED -> true
    }

private fun QualifiedIDEntity.avsId(): String = "$value@$domain"
