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

@Suppress("LongParameterList")
public class AvsCallNotification(
    public val payload: ByteArray,
    public val currentTimeSeconds: UInt,
    public val messageTimeSeconds: UInt,
    public val conversationId: String,
    public val senderUserId: String,
    public val senderClientId: String,
    public val conversationType: AvsCallNotificationConversationType
)

@JvmInline
public value class AvsCallNotificationConversationType(public val value: Int) {
    public companion object {
        public val OneOnOne: AvsCallNotificationConversationType = AvsCallNotificationConversationType(0)
        public val Group: AvsCallNotificationConversationType = AvsCallNotificationConversationType(1)
        public val Conference: AvsCallNotificationConversationType = AvsCallNotificationConversationType(2)
        public val ConferenceMls: AvsCallNotificationConversationType = AvsCallNotificationConversationType(3)
        public val Unknown: AvsCallNotificationConversationType = AvsCallNotificationConversationType(-1)
    }
}
