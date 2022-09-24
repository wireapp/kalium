package com.wire.kalium.network.api.base.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
data class AccessTokenDTO(
    @SerialName("user") val userId: NonQualifiedUserId,
    @SerialName("access_token") val value: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("token_type") val tokenType: String
)

internal fun AccessTokenDTO.toSessionDto(refreshToken: String, qualifiedID: QualifiedID): SessionDTO = SessionDTO(
    userId = qualifiedID,
    tokenType = tokenType,
    accessToken = value,
    refreshToken = refreshToken
)

@JvmInline
value class RefreshTokenDTO(val value: String)
