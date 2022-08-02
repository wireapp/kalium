package com.wire.kalium.network.api

import com.wire.kalium.network.api.model.UserDTO

data class SessionDTO(
    val userId: QualifiedID,
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String
)

data class AuthenticationResultDTO(val sessionDTO: SessionDTO, val userDTO: UserDTO)
