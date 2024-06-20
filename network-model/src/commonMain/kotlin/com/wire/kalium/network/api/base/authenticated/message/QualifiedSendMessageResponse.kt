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
sealed class QualifiedSendMessageResponse {
    @SerialName("time")
    abstract val time: String

    @SerialName("missing")
    abstract val missing: QualifiedUserIdToClientMap

    @SerialName("redundant")
    abstract val redundant: QualifiedUserIdToClientMap

    @SerialName("deleted")
    abstract val deleted: QualifiedUserIdToClientMap

    @SerialName("failed_to_confirm_clients")
    abstract val failedToConfirmClients: QualifiedUserIdToClientMap?

    @Serializable
    data class MissingDevicesResponse(
        @SerialName("time")
        override val time: String,
        @SerialName("missing")
        override val missing: QualifiedUserIdToClientMap,
        @SerialName("redundant")
        override val redundant: QualifiedUserIdToClientMap,
        @SerialName("deleted")
        override val deleted: QualifiedUserIdToClientMap,
        @SerialName("failed_to_confirm_clients")
        override val failedToConfirmClients: QualifiedUserIdToClientMap? = null
    ) : QualifiedSendMessageResponse()

    @Serializable
    data class MessageSent(
        @SerialName("time")
        override val time: String,
        @SerialName("missing")
        override val missing: QualifiedUserIdToClientMap,
        @SerialName("redundant")
        override val redundant: QualifiedUserIdToClientMap,
        @SerialName("deleted")
        override val deleted: QualifiedUserIdToClientMap,
        @SerialName("failed_to_confirm_clients")
        override val failedToConfirmClients: QualifiedUserIdToClientMap? = null
    ) : QualifiedSendMessageResponse()
}

typealias QualifiedUserIdToClientMap = Map<String, Map<String, List<String>>>
