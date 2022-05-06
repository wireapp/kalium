package com.wire.kalium.network.api.message

import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.exceptions.ProteusClientsChangedError
import com.wire.kalium.network.exceptions.SendMessageError
import com.wire.kalium.network.serialization.XProtoBuf
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MessageApiImp(
    private val httpClient: HttpClient,
    private val envelopeProtoMapper: EnvelopeProtoMapper
) : MessageApi {

    @Serializable
    internal data class RequestBody(
        @SerialName("sender") val sender: String,
        @SerialName("data") val `data`: String?,
        @SerialName("native_push") val nativePush: Boolean,
        @SerialName("recipients") val recipients: UserToClientToEncMsgMap,
        @SerialName("transient") val transient: Boolean,
        @SerialName("report_missing") var reportMissing: List<String>? = null,
        @SerialName("native_priority") val priority: MessagePriority
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
    ): NetworkResponse<SendMessageResponse> {

        suspend fun performRequest(
            queryParameter: String?,
            queryParameterValue: Any?,
            body: RequestBody
        ): NetworkResponse<SendMessageResponse> = wrapKaliumResponse<SendMessageResponse.MessageSent>({
            if (it.status.value != 412) null
            else NetworkResponse.Error(kException = SendMessageError.MissingDeviceError(errorBody = it.body()))
        }) {
            httpClient.post("$PATH_CONVERSATIONS/$conversationId$PATH_OTR_MESSAGE") {
                if (queryParameter != null) {
                    parameter(queryParameter, queryParameterValue)
                }
                setBody(body)
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
            is MessageApi.MessageOption.ReportSome -> {
                val body = parameters.toRequestBody()
                body.reportMissing = option.userIDs
                return performRequest(null, null, body)
            }
        }
    }

    override suspend fun qualifiedSendMessage(
        parameters: MessageApi.Parameters.QualifiedDefaultParameters,
        conversationId: ConversationId
    ): NetworkResponse<QualifiedSendMessageResponse> = wrapKaliumResponse<QualifiedSendMessageResponse.MessageSent>({
        if (it.status.value != 412) null
        else NetworkResponse.Error(
            kException = ProteusClientsChangedError(
                errorBody = it.body()
            )
        )
    }) {
        httpClient.post("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}$PATH_PROTEUS_MESSAGE") {
            setBody(envelopeProtoMapper.encodeToProtobuf(parameters))
            contentType(ContentType.Application.XProtoBuf)
        }
    }

    private companion object {
        const val PATH_OTR_MESSAGE = "/otr/messages"
        const val PATH_PROTEUS_MESSAGE = "/proteus/messages"
        const val PATH_CONVERSATIONS = "/conversations"
        const val QUERY_IGNORE_MISSING = "ignore_missing"
        const val QUERY_REPORT_MISSING = "report_missing"
    }
}
