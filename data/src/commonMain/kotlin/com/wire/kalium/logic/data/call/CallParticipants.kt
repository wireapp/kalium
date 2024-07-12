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

package com.wire.kalium.logic.data.call

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("EnforceSerializableFields")
@Serializable
data class CallParticipants(
    @SerialName("convid")
    val conversationId: String,
    val members: List<CallMember>
)

@Suppress("EnforceSerializableFields")
@Serializable
data class CallMember(
    @SerialName("userid")
    val userId: String,
    @SerialName("clientid")
    val clientId: String,
    val aestab: Int,
    val vrecv: Int,
    @SerialName("muted")
    val isMuted: Int
)
