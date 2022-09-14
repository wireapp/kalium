package com.wire.kalium.testservice.models

data class SendPingRequest(
    val conversationDomain: String = "staging.zinfra.io",
    val conversationId: String = ""
)
