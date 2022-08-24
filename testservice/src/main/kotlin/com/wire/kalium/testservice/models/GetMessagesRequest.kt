package com.wire.kalium.testservice.models

data class GetMessagesRequest(
    val conversationDomain: String = "staging.zinfra.io",
    val conversationId: String = ""
)
