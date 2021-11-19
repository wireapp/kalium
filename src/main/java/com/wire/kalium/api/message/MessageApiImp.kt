package com.wire.kalium.api.message

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.api.isSuccessful
import com.wire.kalium.api.message.MessageApi.Companion.PATH_OTR_MESSAGE
import com.wire.kalium.api.message.MessageApi.Companion.QUERY_IGNORE_MISSING
import com.wire.kalium.api.user.client.ClientApi.Companion.BASE_URL
import com.wire.kalium.api.wrapKaliumResponse
import com.wire.kalium.exceptions.AuthException
import com.wire.kalium.models.outbound.otr.OtrMessage
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders


class MessageApiImp(private val httpClient: HttpClient) : MessageApi {
    override suspend fun sendMessage(
            sendMessageRequest: SendMessageRequest,
            conversationId: String,
            ignoreMissing: Boolean,
            token: String
    ): KaliumHttpResult<SendMessageResponse> {
        val response = httpClient.post<HttpResponse>(urlString = "$BASE_URL/$conversationId$PATH_OTR_MESSAGE") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter(QUERY_IGNORE_MISSING, ignoreMissing)
            body = sendMessageRequest
        }
        return if (response.status.value == 412) {
            wrapKaliumResponse<MissingDevicesResponse> {
                response.receive()
            }
        } else if (response.isSuccessful()) {
            wrapKaliumResponse<MessageSent> {
                response.receive()
            }
        } else {
            throw AuthException(code = response.status.value, message = response.status.description)
        }
    }

    override suspend fun sendPartialMessage(otrMessage: OtrMessage, conversationID: String, userId: String): KaliumHttpResult<SendMessageResponse> {
        TODO("Not yet implemented")
    }
}
