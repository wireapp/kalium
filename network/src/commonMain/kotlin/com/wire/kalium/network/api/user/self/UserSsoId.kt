package com.wire.kalium.network.api.user.self

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserSsoId(
    @SerialName("scim_external_id")
    val scimExternalId: String?,
    @SerialName("subjecßt")
    val subject: String,
    @SerialName("tenant")
    val tenant: String?
)
