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
package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.ConversationId

class CallQualityDataProfile(
    data: Map<ConversationId, CallQualityData> = emptyMap()
) : Map<ConversationId, CallQualityData> by data {
    fun plus(conversationId: ConversationId, data: CallQualityData) = CallQualityDataProfile(this + (conversationId to data))
}

data class CallQualityData(
    val quality: CallQuality,
    val roundTripTimeInMilliseconds: Int,
    val upstreamPacketLossPercentage: Int,
    val downstreamPacketLossPercentage: Int,
)

enum class CallQuality {
    UNKNOWN, NORMAL, MEDIUM, POOR, NETWORK_PROBLEM, RECONNECTING;

    val isLowQuality: Boolean get() = this >= POOR

    companion object {
        fun fromInt(quality: Int): CallQuality = CallQuality.entries.firstOrNull { it.ordinal == quality } ?: UNKNOWN
    }
}
