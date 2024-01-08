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

package com.wire.kalium.network.api.base.authenticated.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SendMessageResponse {
    abstract val time: String
    abstract val missing: UserIdToClientMap
    abstract val redundant: UserIdToClientMap
    abstract val deleted: UserIdToClientMap

    @Serializable
    data class MissingDevicesResponse(
        override val time: String,
        override val missing: UserIdToClientMap,
        override val redundant: UserIdToClientMap,
        override val deleted: UserIdToClientMap
    ) : SendMessageResponse()

    @Serializable
    data class MessageSent(
        override val time: String,
        override val missing: UserIdToClientMap,
        override val redundant: UserIdToClientMap,
        override val deleted: UserIdToClientMap
    ) : SendMessageResponse()
}

typealias UserIdToClientMap = Map<String, List<String>>

@Serializable
enum class MessagePriority {
    @SerialName("low")
    LOW,

    @SerialName("high")
    HIGH;

    override fun toString(): String {
        return this.name.lowercase()
    }
}
