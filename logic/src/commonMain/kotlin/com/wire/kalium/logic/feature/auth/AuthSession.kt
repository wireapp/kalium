package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.id.QualifiedID
import kotlinx.serialization.Serializable

@Serializable
data class AuthSession(
    val userId: QualifiedID,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val serverConfig: ServerConfig
)
