/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEventsRequest
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeFully
import okio.Source
import okio.buffer
import okio.use

internal open class NomadDeviceSyncApiV0 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : NomadDeviceSyncApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun postMessageEvents(request: NomadMessageEventsRequest): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post(PATH_MESSAGE_EVENTS) {
                setBody(request)
                contentType(ContentType.Application.Json)
            }
        }

    override suspend fun uploadCryptoState(
        backupSource: () -> Source,
        backupSize: Long
    ): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post(NomadDeviceSyncApi.PATH_CRYPTO_STATE) {
                setBody(CryptoStateBodyContent(backupSource, backupSize))
                contentType(ContentType.Application.OctetStream)
            }
        }

    private class CryptoStateBodyContent(
        private val backupSource: () -> Source,
        private val backupSize: Long
    ) : OutgoingContent.WriteChannelContent() {
        override val contentLength: Long = backupSize

        override suspend fun writeTo(channel: ByteWriteChannel) {
            backupSource().buffer().use { source ->
                val buffer = ByteArray(BUFFER_SIZE)
                var readBytes = source.read(buffer)
                while (readBytes > 0) {
                    channel.writeFully(buffer, 0, readBytes)
                    readBytes = source.read(buffer)
                }
            }
            channel.flush()
            channel.close()
        }
    }

    private companion object {
        const val PATH_MESSAGE_EVENTS = "message/events"
        const val BUFFER_SIZE = 8 * 1024
    }
}
