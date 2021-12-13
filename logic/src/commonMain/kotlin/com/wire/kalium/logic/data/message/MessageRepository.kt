package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.feature.message.MessageEnvelope
import com.wire.kalium.network.api.message.MessageApi

class MessageRepository(
    private val messageApi: MessageApi
) {

    //TODO Rework after NetworkResponse in PR #151
    // Again, Either maybe?
    suspend fun sendEnvelope(envelope: MessageEnvelope) {

    }


}
