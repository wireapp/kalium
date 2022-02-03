package com.wire.kalium.persistence.model

import com.wire.kalium.persistence.network_config.NetworkConfig
import kotlinx.serialization.Serializable

@Serializable
data class PersistenceSession(
    // TODO: replace string with UserId value/domain
    val userId: String,
    //val clientId: String,
    //val domain: String,
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String,
    val networkConfig: NetworkConfig
)
