package com.wire.kalium.testservice.models

data class SendFileRequest (
    val audio: Audio? = null,
    val conversationDomain: String = "",
    val conversationId: String = "",
    val data: String = "",
    val fileName: String = "",
    val expectsReadConfirmation: Boolean = false,
    val invalidHash: Boolean = false,
    val legalHoldStatus: Int = 0,
    val messageTimer: Int = 0,
    val otherAlgorithm: Boolean = false,
    val otherHash: Boolean = false,
    val type: String = "",
    val video: Video? = null
)
