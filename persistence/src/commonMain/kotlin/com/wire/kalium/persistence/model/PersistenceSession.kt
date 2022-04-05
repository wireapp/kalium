package com.wire.kalium.persistence.model

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.serialization.Serializable

@Serializable
data class ServerConfigEntity(
    val apiBaseUrl: String,
    val accountBaseUrl: String,
    val webSocketBaseUrl: String,
    val blackListUrl: String,
    val teamsUrl: String,
    val websiteUrl: String,
    val title: String
)

@Serializable
data class PersistenceSession(
    val userId: QualifiedIDEntity,
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String,
    val serverConfigEntity: ServerConfigEntity
)
