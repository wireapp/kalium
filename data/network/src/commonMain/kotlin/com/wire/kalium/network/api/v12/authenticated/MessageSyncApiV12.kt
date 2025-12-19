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

package com.wire.kalium.network.api.v12.authenticated

import com.wire.kalium.network.api.model.DeleteMessagesResponseDTO
import com.wire.kalium.network.api.model.MessageSyncFetchResponseDTO
import com.wire.kalium.network.api.model.MessageSyncRequestDTO
import com.wire.kalium.network.api.v11.authenticated.MessageSyncApiV11
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import com.wire.kalium.network.utils.wrapRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal open class MessageSyncApiV12(
    private val httpClient: HttpClient,
    private val backupServiceUrl: String = "https://replica.wdebug.link:4545"
) : MessageSyncApiV11() {

    override suspend fun syncMessages(request: MessageSyncRequestDTO): NetworkResponse<Unit> =
        wrapRequest {
            httpClient.post("$backupServiceUrl/messages") {
                setBody(request)
            }
        }

    override suspend fun fetchMessages(
        userId: String,
        since: Long?,
        conversationId: String?,
        order: String,
        size: Int
    ): NetworkResponse<MessageSyncFetchResponseDTO> =
        wrapRequest {
            httpClient.get("$backupServiceUrl/messages") {
                parameter("user", userId)
                since?.let { parameter("since", it) }
                conversationId?.let { parameter("conversation", it) }
                parameter("order", order)
                parameter("size", size)
            }
        }

    override suspend fun deleteMessages(
        userId: String?,
        conversationId: String?,
        before: Long?
    ): NetworkResponse<DeleteMessagesResponseDTO> =
        wrapRequest {
            httpClient.delete("$backupServiceUrl/messages") {
                userId?.let { parameter("user_id", it) }
                conversationId?.let { parameter("conversation_id", it) }
                before?.let { parameter("before", it) }
            }
        }
}
