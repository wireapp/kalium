/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.message

import com.wire.kalium.network.api.base.authenticated.message.QualifiedSendMessageResponse

/**
 * Maps the [QualifiedSendMessageResponse] to a [MessageSent] object.
 * This mapper is useful in case we receive a successful response from the backend, but there are some
 * users that failed to receive the message. ie: federated users and/or conversations.
 */
object SendMessagePartialFailureMapperImpl {
    fun fromDTO(sendMessageResponse: QualifiedSendMessageResponse.MessageSent): MessageSent {
        return MessageSent(
            time = sendMessageResponse.time,
            failed = sendMessageResponse.failed.orEmpty()
        )
    }
}

data class MessageSent(
    val time: String,
    val missing: Map<String, Map<String, List<String>>> = mapOf(),
    val redundant: Map<String, Map<String, List<String>>> = mapOf(),
    val deleted: Map<String, Map<String, List<String>>> = mapOf(),
    val failed: Map<String, Map<String, List<String>>> = mapOf(),
)
