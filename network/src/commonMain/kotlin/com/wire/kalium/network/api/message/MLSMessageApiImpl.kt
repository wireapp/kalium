package com.wire.kalium.network.api.message

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.serialization.Mls
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class MLSMessageApiImpl internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : MLSMessageApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun sendMessage(message: MLSMessageApi.Message): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post(PATH_MESSAGE) {
                setBody(message.value)
                contentType(ContentType.Message.Mls)
            }
        }

    override suspend fun sendWelcomeMessage(message: MLSMessageApi.WelcomeMessage): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post(PATH_WELCOME_MESSAGE) {
                contentType(ContentType.Message.Mls)
                setBody(message.value)
            }
        }

    private companion object {
        const val PATH_MESSAGE = "mls/messages"
        const val PATH_WELCOME_MESSAGE = "mls/welcome"
    }
}
