package com.wire.kalium.network.api.notification.user

import com.wire.kalium.network.api.NonQualifiedUserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NewClientEventData(
    @SerialName("id") val id: String,
    @SerialName("time") val registrationTime: String,
    @SerialName("model") val model: String?,
    @SerialName("type") val deviceType: String,
    @SerialName("class") val deviceClass: String,
    @SerialName("label") val label: String?
)

@Serializable
data class RemoveClientEventData(
    @SerialName("id") val clientId: String
)

@Serializable
data class UserUpdateEventData(
    @SerialName("id") val nonQualifiedUserId: NonQualifiedUserId,
    @SerialName("accent_id") val accentId: Int?,
    @SerialName("sso_id_deleted") val ssoIdDeleted: Boolean
)
