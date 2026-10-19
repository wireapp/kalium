/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlin.time.Instant

/** CallKit presentation after receiving, matching the iOS NSE's start/process/end batch. */
data class ReceivedNotificationResult(
    val message: ReceivedMessageResult?,
    val calls: List<ReceivedCallResult> = emptyList(),
    val presentationReason: String? = null,
    val callSignalsPending: Boolean = false,
    /** Retained ordered Events work, consumed by the existing app incremental sync. */
    val appWorkPending: NotificationAppWork? = null,
    /** Every queued event through the target was processed without deferral or app handoff; stored alerts may be presented. */
    val prefixComplete: Boolean = false,
    /** Why a sync-to-live scan stopped: QUEUE_END, BUDGET, or the barrier or handoff reason. */
    val stoppedReason: String? = null,
)

enum class ReceivedCallType { INCOMING, MISSED, CLOSED }

data class ReceivedCallResult(
    val type: ReceivedCallType,
    val conversationValue: String,
    val conversationDomain: String,
    val callerValue: String,
    val callerDomain: String,
    val callerClientId: String?,
    val messageTimeSeconds: Long,
    val shouldRing: Boolean,
    val isVideoCall: Boolean,
    val closeReason: Int? = null,
    val callerName: String? = null,
    val handoffId: String? = null,
    val shouldNotify: Boolean = false,
    val conversationName: String? = null,
    val senderName: String? = null,
    val isGroupConversation: Boolean = false,
)

data class ReceivedCallEvent(
    val payload: String,
    val conversationId: QualifiedIDEntity,
    val senderId: QualifiedIDEntity,
    val senderClientId: String,
    val time: Instant,
    val isGroup: Boolean,
    val isMLS: Boolean,
    val messageId: String = "",
)

internal data class NotificationEffectsResult(
    val message: ReceivedMessageResult?,
    val calls: List<ReceivedCallEvent>,
)
