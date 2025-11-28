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

package com.wire.kalium.network.api.authenticated.connection

import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.UserId
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionResponse(
    @SerialName("connections") val connections: List<ConnectionDTO>,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("paging_state") val pagingState: String
)

@Serializable
data class ConnectionDTO(
    @SerialName("conversation") val conversationId: String,
    @SerialName("from") val from: String,
    @SerialName("last_update") val lastUpdate: Instant,
    @SerialName("qualified_conversation") val qualifiedConversationId: ConversationId,
    @SerialName("qualified_to") val qualifiedToId: UserId,
    @SerialName("status") val status: ConnectionStateDTO,
    @SerialName("to") val toId: String
)

@Serializable
enum class ConnectionStateDTO {
    /** The other user has sent a connection request to this one */
    @SerialName("pending")
    PENDING,

    /** This user has sent a connection request to another user */
    @SerialName("sent")
    SENT,

    /** The user has been blocked */
    @SerialName("blocked")
    BLOCKED,

    /** The connection has been ignored */
    @SerialName("ignored")
    IGNORED,

    /** The connection has been cancelled */
    @SerialName("cancelled")
    CANCELLED,

    /** The connection is missing legal hold consent  */
    @SerialName("missing-legalhold-consent")
    MISSING_LEGALHOLD_CONSENT,

    /** The connection is complete and the conversation is in its normal state */
    @SerialName("accepted")
    ACCEPTED
}
