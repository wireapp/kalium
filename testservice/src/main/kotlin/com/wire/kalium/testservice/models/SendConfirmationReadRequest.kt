package com.wire.kalium.testservice.models

data class SendConfirmationReadRequest(
    val conversationDomain: String = "staging.zinfra.io",
    val conversationId: String = "",
    val firstMessageId: String = "",
    val moreMessageIds: List<String>? = null
)
