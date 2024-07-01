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

package com.wire.kalium.network.api.v5.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.authenticated.message.SendMLSMessageResponse
import com.wire.kalium.network.api.v4.authenticated.MLSMessageApiV4
import com.wire.kalium.network.serialization.Mls
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.handleUnsuccessfulResponse
import com.wire.kalium.network.utils.wrapFederationResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal open class MLSMessageApiV5 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : MLSMessageApiV4() {
    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun sendMessage(message: MLSMessageApi.Message): NetworkResponse<SendMLSMessageResponse> =
        wrapKaliumResponse {
            httpClient.post(PATH_MESSAGE) {
                setBody(message.value)
                contentType(ContentType.Message.Mls)
            }
        }

    override suspend fun sendCommitBundle(bundle: MLSMessageApi.CommitBundle): NetworkResponse<SendMLSMessageResponse> =
        wrapKaliumResponse(unsuccessfulResponseOverride = { response ->
            wrapFederationResponse(response, delegatedHandler = { handleUnsuccessfulResponse(response) })
        }) {
            httpClient.post(PATH_COMMIT_BUNDLES) {
                setBody(bundle.value)
                contentType(ContentType.Message.Mls)
            }
        }

    private companion object {
        const val PATH_COMMIT_BUNDLES = "mls/commit-bundles"
        const val PATH_MESSAGE = "mls/messages"
    }
}
