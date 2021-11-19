package com.wire.kalium.api.message

import com.wire.kalium.api.KaliumHttpResult

interface MessageApi {

    sealed interface SendMessageOptionsInterface
    sealed class MessageOption : SendMessageOptionsInterface {
        object IgnoreAll: MessageOption()
        object ReportAll: MessageOption()
        data class IgnoreSome(val userIDs: List<String>): MessageOption()
        data class ReposeSome(val userIDs: List<String>): MessageOption()
    }

    sealed interface SendMessageParameters
    sealed class Parameters : SendMessageParameters {
        data class DefaultParameters(
           val sender: String,
           val `data`: String,
           val nativePush: Boolean,
           val recipients: HashMap<String, HashMap<String, String>>,
           val transient: Boolean,
           val priority: String = "low"): Parameters()
    }

    suspend fun sendMessage(
            parameters: Parameters.DefaultParameters,
            conversationId: String,
            option: MessageOption,
            token: String
    ): KaliumHttpResult<SendMessageResponse>

    companion object {
        const val PATH_OTR_MESSAGE = "/otr/messages"
        const val QUERY_IGNORE_MISSING = "ignore_missing"
        const val QUERY_REPORT_MISSING = "report_missing"
    }
}
