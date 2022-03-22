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
typealias MLSPublicKey = String

@Serializable
data class QualifiedID(
    @SerialName("id")
    val value: String,
    @SerialName("domain")
    val domain: String
)

@Serializable
data class UserSsoId(
    @SerialName("scim_external_id")
    val scimExternalId: String?,
    @SerialName("subject")
    val subject: String,
    @SerialName("tenant")
    val tenant: String?
)
