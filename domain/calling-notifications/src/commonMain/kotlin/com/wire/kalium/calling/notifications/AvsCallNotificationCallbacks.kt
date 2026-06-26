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

package com.wire.kalium.calling.notifications

import kotlin.jvm.JvmInline

public interface AvsCallNotificationCallbacks {
    public fun onIncomingCall(incomingCall: AvsIncomingCallNotification)
    public fun onMissedCall(missedCall: AvsMissedCallNotification)
    public fun onClosedCall(closedCall: AvsClosedCallNotification)
}

public data class AvsIncomingCallNotification(
    public val conversationId: String,
    public val messageTimeSeconds: UInt,
    public val callerUserId: String,
    public val callerClientId: String?,
    public val isVideoCall: Boolean,
    public val shouldRing: Boolean,
    public val conversationType: AvsCallNotificationConversationType
)

public data class AvsMissedCallNotification(
    public val conversationId: String,
    public val messageTimeSeconds: UInt,
    public val callerUserId: String,
    public val isVideoCall: Boolean
)

public data class AvsClosedCallNotification(
    public val reason: AvsCallNotificationClosedReason,
    public val conversationId: String,
    public val messageTimeSeconds: UInt,
    public val callerUserId: String,
    public val callerClientId: String?
)

@JvmInline
public value class AvsCallNotificationClosedReason(public val value: Int) {
    public companion object {
        public val Normal: AvsCallNotificationClosedReason = AvsCallNotificationClosedReason(0)
        public val Error: AvsCallNotificationClosedReason = AvsCallNotificationClosedReason(1)
        public val Timeout: AvsCallNotificationClosedReason = AvsCallNotificationClosedReason(2)
        public val LostMedia: AvsCallNotificationClosedReason = AvsCallNotificationClosedReason(3)
        public val Cancelled: AvsCallNotificationClosedReason = AvsCallNotificationClosedReason(4)
        public val AnsweredElsewhere: AvsCallNotificationClosedReason = AvsCallNotificationClosedReason(5)
        public val IoError: AvsCallNotificationClosedReason = AvsCallNotificationClosedReason(6)
        public val StillOngoing: AvsCallNotificationClosedReason = AvsCallNotificationClosedReason(7)
        public val TimeoutConnection: AvsCallNotificationClosedReason = AvsCallNotificationClosedReason(8)
        public val DataChannel: AvsCallNotificationClosedReason = AvsCallNotificationClosedReason(9)
        public val Rejected: AvsCallNotificationClosedReason = AvsCallNotificationClosedReason(10)
        public val OutdatedClient: AvsCallNotificationClosedReason = AvsCallNotificationClosedReason(11)
        public val NoOneJoined: AvsCallNotificationClosedReason = AvsCallNotificationClosedReason(12)
        public val EveryoneLeft: AvsCallNotificationClosedReason = AvsCallNotificationClosedReason(13)
    }
}
