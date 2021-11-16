package com.wire.kalium.api.message

import com.wire.kalium.models.outbound.otr.OtrMessage

interface MessageApi {

    fun sendMessage(conversationID: String, msg: OtrMessage, ignoreMissing: Boolean = false)

    companion object {
        protected const val PATH_MESSAGE = "otr/messages"
        protected const val QUERY_IGNORE_MISSING = "ignore_missing"
    }
}
