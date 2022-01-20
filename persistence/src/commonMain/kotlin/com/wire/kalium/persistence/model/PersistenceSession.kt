package com.wire.kalium.persistence.model

data class PersistenceSession(
    val userId: String,
    //val clientId: String,
    //val domain: String,
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String
)
