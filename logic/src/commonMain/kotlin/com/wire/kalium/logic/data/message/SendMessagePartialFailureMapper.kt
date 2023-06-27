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

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.message.QualifiedSendMessageResponse
import com.wire.kalium.network.api.base.authenticated.message.QualifiedUserIdToClientMap

/**
 * Maps the [QualifiedSendMessageResponse] to a [MessageSent] object.
 * This mapper is useful in case we receive a successful response from the backend, but there are some
 * users that failed to receive the message. ie: federated users and/or conversations.
 */
interface SendMessagePartialFailureMapper {
    fun fromDTO(sendMessageResponse: QualifiedSendMessageResponse): MessageSent
}

internal class SendMessagePartialFailureMapperImpl : SendMessagePartialFailureMapper {
    override fun fromDTO(sendMessageResponse: QualifiedSendMessageResponse): MessageSent {
        return when (sendMessageResponse) {
            is QualifiedSendMessageResponse.MessageSent -> MessageSent(
                time = sendMessageResponse.time,
                failed = mapNestedUsersIntoUserIds(sendMessageResponse.failed)
            )
            // This case is not expected when sending a msg succeeds, this is a shared response when receiving 412: [MissingDevicesResponse]
            is QualifiedSendMessageResponse.MissingDevicesResponse -> MessageSent(
                time = sendMessageResponse.time,
                missing = mapNestedUsersIntoUserIds(sendMessageResponse.missing)
            )
        }
    }

    private fun mapNestedUsersIntoUserIds(nestedUsersMap: QualifiedUserIdToClientMap?) =
        nestedUsersMap
            ?.map { it.key to it.value.keys }
            ?.map { (domain, userIds) ->
                userIds.map { user -> UserId(user, domain) }
            }?.flatten().orEmpty()
}

data class MessageSent(
    val time: String,
    val failed: List<UserId> = listOf(),
    val missing: List<UserId> = listOf()
)
