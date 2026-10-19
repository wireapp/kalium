/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.network.api.v19.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.authenticated.message.SendMLSMessageResponse
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.v18.authenticated.MLSMessageApiV18
import com.wire.kalium.network.serialization.Mls
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal open class MLSMessageApiV19 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : MLSMessageApiV18(authenticatedNetworkClient) {
    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun sendTargetedMessage(
        batch: MLSMessageApi.TargetedMessageBatch
    ): NetworkResponse<SendMLSMessageResponse> =
        wrapRequest {
            httpClient.post(PATH_TARGETED_MESSAGE) {
                setBody(batch.value)
                contentType(ContentType.Message.Mls)
            }
        }

    override suspend fun sendTransientMessage(
        message: MLSMessageApi.TransientMessage
    ): NetworkResponse<SendMLSMessageResponse> =
        wrapRequest {
            httpClient.post(PATH_TRANSIENT_MESSAGE) {
                setBody(message.value)
                contentType(ContentType.Message.Mls)
            }
        }

    override suspend fun sendTransientTargetedMessage(
        batch: MLSMessageApi.TargetedMessageBatch
    ): NetworkResponse<SendMLSMessageResponse> =
        wrapRequest {
            httpClient.post(PATH_TRANSIENT_TARGETED_MESSAGE) {
                setBody(batch.value)
                contentType(ContentType.Message.Mls)
            }
        }

    private companion object {
        const val PATH_TARGETED_MESSAGE = "mls/messages/targeted"
        const val PATH_TRANSIENT_MESSAGE = "mls/messages/transient"
        const val PATH_TRANSIENT_TARGETED_MESSAGE = "mls/messages/transient-targeted"
    }
}
