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

package com.wire.kalium.network.api.authenticated.conversation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemberUpdateDTO(
    @SerialName("hidden") val hidden: Boolean? = null,
    @SerialName("hidden_ref") val hiddenRef: String? = null,
    @SerialName("otr_archived") val otrArchived: Boolean? = null,
    @SerialName("otr_archived_ref") val otrArchivedRef: String? = null,
    @SerialName("otr_muted_ref") val otrMutedRef: String? = null,
    @SerialName("otr_muted_status") @Serializable(with = MutedStatusSerializer::class) val otrMutedStatus: MutedStatus? = null
)

enum class MutedStatus {
    /**
     * 0 -> All notifications are displayed
     */
    ALL_ALLOWED,

    /**
     * 1 -> Only mentions are displayed (normal messages muted)
     */
    ONLY_MENTIONS_ALLOWED,

    /**
     * 2 -> Only normal notifications are displayed (mentions are muted) -- legacy, not used
     */
    MENTIONS_MUTED,

    /**
     * 3 -> No notifications are displayed
     */
    ALL_MUTED;

    companion object {
        fun fromOrdinal(ordinal: Int): MutedStatus? = values().firstOrNull { ordinal == it.ordinal }
    }
}
