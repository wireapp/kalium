package com.wire.kalium.api.message

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.api.isSuccessful
import com.wire.kalium.api.message.MessageApi.Companion.PATH_OTR_MESSAGE
import com.wire.kalium.api.message.MessageApi.Companion.QUERY_IGNORE_MISSING
import com.wire.kalium.api.message.MessageApi.Companion.QUERY_REPORT_MISSING
import com.wire.kalium.api.user.client.ClientApi.Companion.BASE_URL
import com.wire.kalium.api.wrapKaliumResponse
import com.wire.kalium.exceptions.AuthException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MessageApiImp(private val httpClient: HttpClient) : MessageApi {

    @Serializable
    internal data class RequestBody(
        @SerialName("sender") val sender: String,
        @SerialName("data") val `data`: String,
        @SerialName("native_push") val nativePush: Boolean,
        @SerialName("recipients") val recipients: HashMap<String, HashMap<String, String>>,
        @SerialName("transient") val transient: Boolean,
        @SerialName("report_missing") var reportMissing: List<String>? = null,
        @SerialName("native_priority") val priority: String = "low"
    )

    private fun MessageApi.Parameters.DefaultParameters.toRequestBody(): RequestBody = RequestBody(
        sender = this.sender,
        data = this.data,
        nativePush = this.nativePush,
        recipients = this.recipients,
        transient = this.transient,
        priority = this.priority
    )

    override suspend fun sendMessage(
        parameters: MessageApi.Parameters.DefaultParameters,
        conversationId: String,
        option: MessageApi.MessageOption,
        token: String
    ): KaliumHttpResult<SendMessageResponse> {

        suspend fun performRequest(
            queryParameter: String?,
            queryParameterValue: Any?,
            body: RequestBody
        ): HttpResponse {

            return httpClient.post<HttpResponse>(urlString = "$BASE_URL/$conversationId$PATH_OTR_MESSAGE") {
                if(queryParameter != null) {
                    parameter(queryParameter, queryParameterValue)
                }
                header(HttpHeaders.Authorization, "Bearer $token")
                this.body = body
            }
        }

        val response: HttpResponse

        when(option) {
            is MessageApi.MessageOption.IgnoreAll -> {
                val body = parameters.toRequestBody()
                response = performRequest(QUERY_IGNORE_MISSING, false, body)
            }
            is MessageApi.MessageOption.IgnoreSome -> {
                val body = parameters.toRequestBody()
                val commaSeparatedList = option.userIDs.joinToString(",")
                response = performRequest(QUERY_IGNORE_MISSING, commaSeparatedList, body)
            }

            is MessageApi.MessageOption.ReportAll -> {
                val body = parameters.toRequestBody()
                response = performRequest(QUERY_REPORT_MISSING, true, body)
            }
            is MessageApi.MessageOption.ReposeSome -> {
                val body = parameters.toRequestBody()
                body.reportMissing  = option.userIDs
                response = performRequest(null, null, body)
            }
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
}
