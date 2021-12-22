package com.wire.kalium.network.api.message

import com.wire.kalium.network.utils.NetworkResponse

interface MessageApi {

    sealed interface SendMessageOptionsInterface
    sealed class MessageOption : SendMessageOptionsInterface {
        /**
         * All missing recipients clients will be ignored
         * The message will be sent regardless if the recipients list is correct or not
         */
        object IgnoreAll : MessageOption()

        /**
         * All missing recipients clients will be reported http error code 412
         * The message will not be sent unless the list is correct
         */
        object ReportAll : MessageOption()
        data class IgnoreSome(val userIDs: List<String>) : MessageOption()
        data class ReportSome(val userIDs: List<String>) : MessageOption()
    }

    sealed interface SendMessageParameters

    sealed class Parameters : SendMessageParameters {
        /**
         * Otr Message parameters
         * @param sender sender client ID
         * @param recipients Map of userid to clientIds and its preKey
         * @param data extra data (optional)
         * @param nativePush push notification
         * @param priority message priority
         * @param transient
         */
        data class DefaultParameters(
            val sender: String,
            val recipients: UserToClientToEncMsgMap,
            val nativePush: Boolean,
            val priority: MessagePriority,
            val transient: Boolean,
            val `data`: String? = null
        ) : Parameters()
    }

    suspend fun sendMessage(
        parameters: Parameters.DefaultParameters,
        conversationId: String,
        option: MessageOption
    ): NetworkResponse<SendMessageResponse>
}

typealias UserToClientToEncMsgMap = Map<String, Map<String, String>>
