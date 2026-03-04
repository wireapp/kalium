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
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadAllMessagesResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationMetadataResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEventsRequest
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

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

    override suspend fun getAllMessages(): NetworkResponse<NomadAllMessagesResponse> =
        wrapKaliumResponse {
            httpClient.get(PATH_ALL_MESSAGES)
        }

    override suspend fun getConversationMetadata(): NetworkResponse<NomadConversationMetadataResponse> =
        wrapKaliumResponse {
            httpClient.get(PATH_CONVERSATION_METADATA)
        }

    private companion object {
        const val PATH_MESSAGE_EVENTS = "message/events"
        const val PATH_ALL_MESSAGES = "all-messages"
        const val PATH_CONVERSATION_METADATA = "conversation/metadata"
    }
}
