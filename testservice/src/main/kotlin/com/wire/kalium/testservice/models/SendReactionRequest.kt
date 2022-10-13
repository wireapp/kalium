package com.wire.kalium.testservice.models

data class SendReactionRequest(
    val conversationDomain: String = "staging.zinfra.io",
    val conversationId: String = "",
    val legalHoldStatus: Int = 0,
    val originalMessageId: String = "",
    val type: String = "❤️"
)
