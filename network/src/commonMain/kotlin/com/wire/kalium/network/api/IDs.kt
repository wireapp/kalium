package com.wire.kalium.network.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ConversationId = QualifiedID
typealias UserId = QualifiedID

@Serializable
data class QualifiedID(
    @SerialName("id")
    val value: String,

    @SerialName("domain")
    val domain: String
)
