package com.wire.kalium.api.message

import com.wire.kalium.api.message.MessageApi.Companion.PATH_OTR_MESSAGE
import com.wire.kalium.api.message.MessageApi.Companion.QUERY_IGNORE_MISSING
import com.wire.kalium.api.user.client.ClientApi.Companion.BASE_URL
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
    ): SendMessageResponse {
        val response = httpClient.post<HttpResponse>(urlString = "$BASE_URL/$conversationId$PATH_OTR_MESSAGE") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter(QUERY_IGNORE_MISSING, sendMessageRequest)
            body = sendMessageRequest
        }
        if (response.status.value == 412) {
            return response.receive<MissingDevicesResponse>()
        } else if (response.status.value >= 400) {
            throw AuthException(code = response.status.value, message = response.status.description)
        }
        return MessageSent
    }

    override suspend fun sendPartialMessage(otrMessage: OtrMessage, conversationID: String, userId: String): SendMessageResponse {
        TODO("Not yet implemented")
    }
}
