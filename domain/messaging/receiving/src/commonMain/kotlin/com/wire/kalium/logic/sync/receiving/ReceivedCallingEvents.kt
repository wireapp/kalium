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
