package com.wire.kalium.api.message

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.models.outbound.otr.OtrMessage

interface MessageApi {

    suspend fun sendMessage(
            sendMessageRequest: SendMessageRequest,
            conversationId: String,
            ignoreMissing: Boolean,
            token: String
    ): KaliumHttpResult<SendMessageResponse>

    suspend fun sendPartialMessage(otrMessage: OtrMessage, conversationID: String, userId: String): KaliumHttpResult<SendMessageResponse>

    companion object {
        const val PATH_OTR_MESSAGE = "/otr/messages"
        const val QUERY_IGNORE_MISSING = "ignore_missing"
    }
}
