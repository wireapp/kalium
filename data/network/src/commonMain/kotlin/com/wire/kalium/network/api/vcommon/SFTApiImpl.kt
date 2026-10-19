/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.network.api.vcommon

import com.wire.kalium.network.UnboundNetworkClient
import com.wire.kalium.network.api.base.authenticated.SFTApi
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.contentType

/**
 * SFT URLs come from AVS and can point to any host, so this must only ever be given
 * an unauthenticated client. Never back it with the Wire backend client.
 */
internal class SFTApiImpl(
    private val sftNetworkClient: UnboundNetworkClient
) : SFTApi {

    override suspend fun connectToSFT(url: String, data: String): NetworkResponse<ByteArray> =
        wrapRequest {
            // Without a scheme Ktor reads the host as a relative path, and this client has no default host to fall back to.
            URLBuilder(if (url.contains("://")) url else "https://$url").apply {
                protocol = URLProtocol.HTTPS
            }.build().let { parsedUrl ->
                sftNetworkClient.httpClient.post(url = parsedUrl) {
                    contentType(ContentType.Application.Json)
                    // We are parsing the data string to json due to Ktor serialization escaping the string
                    // and thus backend not recognizing and returning a 400 - Bad Request
                    val json = KtxSerializer.json.parseToJsonElement(data)
                    setBody(json)
                }
            }
        }
}
