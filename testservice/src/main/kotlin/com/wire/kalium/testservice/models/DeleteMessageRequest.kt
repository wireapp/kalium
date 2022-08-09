package com.wire.kalium.testservice.models

data class DeleteMessageRequest(
    val conversationDomain: String = "",
    val conversationId: String = "",
    val messageId: String = ""
)
