package com.wire.kalium.network.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ConversationId = QualifiedID
typealias NonQualifiedConversationId = String
typealias UserId = QualifiedID
typealias NonQualifiedUserId = String
typealias TeamId = String
typealias AssetId = String
typealias AssetKey = String

@Serializable
data class QualifiedID(
    @SerialName("id")
    val value: String,

    @SerialName("domain")
    val domain: String
)
