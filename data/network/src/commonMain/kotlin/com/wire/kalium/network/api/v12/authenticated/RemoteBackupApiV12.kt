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

import com.wire.kalium.network.api.authenticated.remoteBackup.DeleteMessagesResponseDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.MessageSyncFetchResponseDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.MessageSyncRequestDTO
import com.wire.kalium.network.api.v0.authenticated.RemoteBackupApiV0
import com.wire.kalium.network.utils.BACKUP_STREAM_BUFFER_SIZE
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.StreamStateBackupContent
import com.wire.kalium.network.utils.wrapRequest
import com.wire.kalium.network.utils.wrapStreamingRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import okio.Sink
import okio.Source
import okio.buffer
import okio.use

internal open class RemoteBackupApiV12(
    private val httpClient: HttpClient,
    private val backupServiceUrl: String?
) : RemoteBackupApiV0() {

    override suspend fun syncMessages(request: MessageSyncRequestDTO): NetworkResponse<Unit> =
        wrapRequest {
            httpClient.post("$backupServiceUrl/messages") {
                setBody(request)
            }
        }

    override suspend fun fetchMessages(
        user: String,
        since: Long?,
        conversation: String?,
        paginationToken: String?,
        size: Int
    ): NetworkResponse<MessageSyncFetchResponseDTO> =
        wrapRequest {
            httpClient.get("$backupServiceUrl/messages") {
                parameter("user", user)
                since?.let { parameter("since", it) }
                conversation?.let { parameter("conversation", it) }
                paginationToken?.let { parameter("pagination_token", it) }
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

    override suspend fun uploadStateBackup(
        userId: String,
        backupDataSource: () -> Source,
        backupSize: Long
    ): NetworkResponse<Unit> =
        wrapRequest {
            httpClient.post("$backupServiceUrl/state") {
                parameter("user_id", userId)
                contentType(ContentType.Application.OctetStream)
                setBody(
                    StreamStateBackupContent(
                        backupDataSource = backupDataSource,
                        backupSize = backupSize
                    )
                )
            }
        }

    override suspend fun downloadStateBackup(
        userId: String,
        tempFileSink: Sink
    ): NetworkResponse<Unit> = wrapStreamingRequest { handleError ->
        httpClient.prepareGet("$backupServiceUrl/state") {
            parameter("user_id", userId)
        }.execute { httpResponse ->
            if (httpResponse.status.isSuccess()) {
                val channel = httpResponse.body<ByteReadChannel>()
                writeChannelToSink(channel, tempFileSink)
                NetworkResponse.Success(Unit, httpResponse)
            } else {
                handleError(httpResponse)
            }
        }
    }

    private suspend fun writeChannelToSink(channel: ByteReadChannel, sink: Sink) {
        sink.buffer().use { bufferedSink ->
            val buffer = ByteArray(BACKUP_STREAM_BUFFER_SIZE)
            while (!channel.isClosedForRead) {
                val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                if (bytesRead <= 0) break
                bufferedSink.write(buffer, 0, bytesRead)
            }
        }
    }
}
