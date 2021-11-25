package com.wire.kalium.api.message

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.api.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
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
            @SerialName("native_priority") val priority: String
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
            option: MessageApi.MessageOption
    ): KaliumHttpResult<SendMessageResponse> {

        suspend fun performRequest(
                queryParameter: String?,
                queryParameterValue: Any?,
                body: RequestBody
        ): KaliumHttpResult<SendMessageResponse> {
            try {
                return wrapKaliumResponse<MessageSent> {
                    httpClient.post<HttpResponse>(path = "$PATH_CONVERSATIONS/$conversationId$PATH_OTR_MESSAGE") {
                        if (queryParameter != null) {
                            parameter(queryParameter, queryParameterValue)
                        }
                        this.body = body
                    }
                }
            } catch (e: ClientRequestException) {
                if (e.response.status.value == 412) {
                    return wrapKaliumResponse<MissingDevicesResponse> { e.response }
                } else {
                    throw e
                }
            }
        }

        when (option) {
            is MessageApi.MessageOption.IgnoreAll -> {
                val body = parameters.toRequestBody()
                return performRequest(QUERY_IGNORE_MISSING, true, body)
            }
            is MessageApi.MessageOption.IgnoreSome -> {
                val body = parameters.toRequestBody()
                val commaSeparatedList = option.userIDs.joinToString(",")
                return performRequest(QUERY_IGNORE_MISSING, commaSeparatedList, body)
            }

            is MessageApi.MessageOption.ReportAll -> {
                val body = parameters.toRequestBody()
                return performRequest(QUERY_REPORT_MISSING, true, body)
            }
            is MessageApi.MessageOption.ReposeSome -> {
                val body = parameters.toRequestBody()
                body.reportMissing = option.userIDs
                return performRequest(null, null, body)
            }
        }
    }

    private companion object {
        const val PATH_OTR_MESSAGE = "/otr/messages"
        const val PATH_CONVERSATIONS = "/conversations"
        const val QUERY_IGNORE_MISSING = "ignore_missing"
        const val QUERY_REPORT_MISSING = "report_missing"
    }
}
