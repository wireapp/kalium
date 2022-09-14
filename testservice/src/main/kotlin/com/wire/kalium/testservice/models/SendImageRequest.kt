package com.wire.kalium.testservice.models

data class SendImageRequest(
    val conversationDomain: String = "staging.zinfra.io",
    val conversationId: String = "",
    val data: String = "",
    val expectsReadConfirmation: Boolean = false,
    val height: Int = 0,
    val invalidHash: Boolean = false,
    val legalHoldStatus: Int = 0,
    val messageTimer: Int = 0,
    val otherAlgorithm: Boolean = false,
    val otherHash: Boolean = false,
    val type: String = "",
    val width: Int = 0
)
