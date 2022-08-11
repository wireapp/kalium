package com.wire.kalium.testservice.models

data class SendPingRequest(
    val conversationDomain: String = "",
    val conversationId: String = ""
)
