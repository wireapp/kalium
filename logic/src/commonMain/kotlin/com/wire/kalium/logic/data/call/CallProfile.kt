package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus

data class CallProfile(
    val calls: Map<String, Call>
) {

    private val callsList get() = calls.values

    operator fun get(conversationId: String): Call? = calls[conversationId]

    fun incomingCalls(): List<Call> = callsList
        .filter { it.status == CallStatus.INCOMING }
}
