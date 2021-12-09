package com.wire.kalium.network.api.message

import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MessageApiImp(private val httpClient: HttpClient) : MessageApi {

    @Serializable
    internal data class RequestBody(
        @SerialName("sender") val sender: String,
        @SerialName("data") val `data`: String,
        @SerialName("native_push") val nativePush: Boolean,
// TODO: Migrate Recipients to this new project
//        @SerialName("recipients") val recipients: Map<String, Map<String, String>>,
        @SerialName("transient") val transient: Boolean,
        @SerialName("report_missing") var reportMissing: List<String>? = null,
        @SerialName("native_priority") val priority: MessagePriority
    )

    private fun MessageApi.Parameters.DefaultParameters.toRequestBody(): RequestBody = RequestBody(
        sender = this.sender,
        data = this.data,
        nativePush = this.nativePush,
//        recipients = this.recipients, // TODO: Migrate Recipients to this new project
        transient = this.transient,
        priority = this.priority
    )

    override suspend fun sendMessage(
        parameters: MessageApi.Parameters.DefaultParameters,
        conversationId: String,
        option: MessageApi.MessageOption
    ): NetworkResponse<SendMessageResponse> {

        suspend fun performRequest(
            queryParameter: String?,
            queryParameterValue: Any?,
            body: RequestBody
        ): NetworkResponse<SendMessageResponse> {
            try {
                return wrapKaliumResponse<SendMessageResponse.MessageSent> {
                    httpClient.post(path = "$PATH_CONVERSATIONS/$conversationId$PATH_OTR_MESSAGE") {
                        if (queryParameter != null) {
                            parameter(queryParameter, queryParameterValue)
                        }
                        this.body = body
                    }
                }
            } catch (e: ClientRequestException) {
                if (e.response.status.value == 412) {
                    return wrapKaliumResponse<SendMessageResponse.MissingDevicesResponse> { e.response }
                } else {
                    throw KaliumException.InvalidRequestError(ErrorResponse(e.response.status.value, e.message, e.toString()), e)
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
