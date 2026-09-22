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
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Clock

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
    val journal = CallSignalJournal(database).signals()
    val messageIds = events.map { it.messageId }.toSet()
    journal.filter { it.messageId in messageIds }.forEach {
        platform.Foundation.NSLog("CALL_FLOW signal=${it.id} stage=durable_ready")
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
    val away = database.userDAO.getUserDetailsByQualifiedID(self.toDao())?.availabilityStatus ==
        com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity.AWAY
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
        val source = journal.lastOrNull {
            it.messageId in messageIds &&
                (result.callerClientId == null || it.senderClientId == result.callerClientId) &&
                it.conversationValue == result.conversationValue && it.conversationDomain == result.conversationDomain &&
                it.senderValue == result.callerValue && it.senderDomain == result.callerDomain &&
                it.timeMillis / MILLISECONDS_PER_SECOND == result.messageTimeSeconds
        }
        platform.Foundation.NSLog("CALL_FLOW call=${source?.id ?: "none"} stage=nse_decision type=${result.type} ring=${result.shouldRing}")
        result.copy(
            callerName = name,
            handoffId = source?.id,
            shouldRing = result.shouldRing && !away && conversation != null &&
                conversation.mutedStatus != ConversationEntity.MutedStatus.ALL_MUTED
        )
    }
}

private fun QualifiedIDEntity.avsId(): String = "$value@$domain"
