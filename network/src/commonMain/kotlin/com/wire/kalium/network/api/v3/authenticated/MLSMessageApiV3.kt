package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.SendMLSMessageResponse
import com.wire.kalium.network.api.v2.authenticated.MLSMessageApiV2
import com.wire.kalium.network.serialization.XProtoBuf
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal open class MLSMessageApiV3 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : MLSMessageApiV2(authenticatedNetworkClient) {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun sendCommitBundle(bundle: MLSMessageApi.CommitBundle): NetworkResponse<SendMLSMessageResponse> =
        wrapKaliumResponse {
            httpClient.post(PATH_COMMIT_BUNDLES) {
                setBody(bundle.value)
                contentType(ContentType.Application.XProtoBuf)
            }
        }

    private companion object {
        const val PATH_COMMIT_BUNDLES = "mls/commit-bundles"
    }
}
