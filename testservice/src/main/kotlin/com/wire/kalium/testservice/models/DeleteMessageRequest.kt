package com.wire.kalium.testservice.models

data class DeleteMessageRequest(
    val conversationDomain: String = "staging.zinfra.io",
    val conversationId: String = "",
    val messageId: String = ""
)
