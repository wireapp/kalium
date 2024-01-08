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
import kotlinx.serialization.json.Json

@Serializable
data class CallClient(
    @SerialName("userid") val userId: String,
    @SerialName("clientid") val clientId: String,
    @SerialName("in_subconv") val isMemberOfSubconversation: Boolean = false
)

@Serializable
data class CallClientList(
    @SerialName("clients") val clients: List<CallClient>
) {
    // TODO(optimization): Use a shared Json instance instead of creating one every time.
    fun toJsonString(): String = Json { isLenient = true }.encodeToString(serializer(), this)
}
