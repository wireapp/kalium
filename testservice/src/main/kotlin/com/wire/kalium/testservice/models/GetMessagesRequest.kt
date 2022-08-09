package com.wire.kalium.testservice.models

data class GetMessagesRequest(
    val conversationDomain: String = "",
    val conversationId: String = ""
)
