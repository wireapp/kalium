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

import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.utils.NetworkResponse

interface MessageApi {

    sealed class QualifiedMessageOption {
        /**
         * All missing recipients clients will be ignored
         * The message will be sent regardless if the recipients list is correct or not
         */
        data object IgnoreAll : QualifiedMessageOption()

        /**
         * All missing recipients clients will be reported http error code 412
         * The message will not be sent unless the list is correct
         */
        data object ReportAll : QualifiedMessageOption()
        data class IgnoreSome(val userIDs: List<UserId>) : QualifiedMessageOption()
        data class ReportSome(val userIDs: List<UserId>) : QualifiedMessageOption()
    }

    sealed interface SendMessageParameters

    sealed class Parameters : SendMessageParameters {
        /**
         * Otr Message parameters
         * @param sender sender client ID
         * @param recipients Map of userid to clientIds and its preKey
         * @param data extra data (optional)
         * @param nativePush push notification
         * @param priority message priority
         * @param transient
         */
        data class DefaultParameters(
            val sender: String,
            val recipients: UserToClientToEncMsgMap,
            val nativePush: Boolean,
            val priority: MessagePriority,
            val transient: Boolean,
            val `data`: String? = null
        ) : Parameters()

        /**
         * Otr Message parameters
         * @param sender sender client ID
         * @param recipients Map of userid to clientIds and its preKey
         * @param externalBlob extra data used for External messages, when the content is too big (optional)
         * @param nativePush push notification
         * @param priority message priority
         * @param transient
         */
        data class QualifiedDefaultParameters(
            val sender: String,
            val recipients: QualifiedUserToClientToEncMsgMap,
            val nativePush: Boolean,
            val priority: MessagePriority,
            val transient: Boolean,
            val externalBlob: ByteArray? = null,
            val messageOption: QualifiedMessageOption
        ) : Parameters()
    }

    suspend fun qualifiedSendMessage(
        parameters: Parameters.QualifiedDefaultParameters,
        conversationId: ConversationId
    ): NetworkResponse<QualifiedSendMessageResponse>

    suspend fun qualifiedBroadcastMessage(
        parameters: Parameters.QualifiedDefaultParameters
    ): NetworkResponse<QualifiedSendMessageResponse>
}

typealias UserToClientToEncMsgMap = Map<String, Map<String, String>>
typealias QualifiedUserToClientToEncMsgMap = Map<UserId, Map<String, ByteArray>>
