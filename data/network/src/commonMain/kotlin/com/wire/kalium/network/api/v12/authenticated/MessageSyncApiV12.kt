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
import kotlinx.coroutines.CancellationException
import okio.Sink
import okio.Source
import okio.buffer
import okio.use

internal open class MessageSyncApiV12(
    private val httpClient: HttpClient,
) : MessageSyncApiV11() {

    override suspend fun syncMessages(request: MessageSyncRequestDTO): NetworkResponse<Unit> =
        wrapRequest {
            httpClient.post("backup/messages") {
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
            httpClient.get("backup/messages") {
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
            httpClient.delete("backup/messages") {
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
            httpClient.post("backup/state") {
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
    ): NetworkResponse<Unit> = runCatching {
        httpClient.prepareGet("backup/state") {
            parameter("user_id", userId)
        }.execute { httpResponse ->
            if (httpResponse.status.isSuccess()) {
                handleStateBackupDownload(httpResponse, tempFileSink)
            } else {
                wrapKaliumResponse { httpResponse }
            }
        }
    }.getOrElse { unhandledException ->
        if (unhandledException is CancellationException) {
            throw unhandledException
        }
        NetworkResponse.Error(com.wire.kalium.network.exceptions.KaliumException.GenericError(unhandledException))
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun handleStateBackupDownload(httpResponse: HttpResponse, tempFileSink: Sink) = try {
        val channel = httpResponse.body<ByteReadChannel>()
        tempFileSink.buffer().use { bufferedSink ->
            val array = ByteArray(16*1024)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(array, 0, array.size)
                if (read <= 0) break
                bufferedSink.write(array, 0, read)
            }
        }
        NetworkResponse.Success(Unit, httpResponse)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        NetworkResponse.Error(com.wire.kalium.network.exceptions.KaliumException.GenericError(e))
    }
}
