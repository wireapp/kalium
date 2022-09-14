package com.wire.kalium.testservice.models

data class SendTextRequest(
    val buttons: List<String> = listOf(),
    val conversationDomain: String = "staging.zinfra.io",
    val conversationId: String = "",
    val expectsReadConfirmation: Boolean = false,
    val legalHoldStatus: Int = 0,
    val linkPreview: LinkPreview? = null,
    val mentions: List<Mention> = listOf(),
    val messageTimer: Int = 0,
    val quote: Quote? = null,
    val text: String? = null
)
