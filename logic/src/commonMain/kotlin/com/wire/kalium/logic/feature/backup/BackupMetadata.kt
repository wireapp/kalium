package com.wire.kalium.logic.feature.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupMetadata(
    @SerialName("platform")
    val platform: String,
    @SerialName("version")
    val version: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("creation_time")
    val creationTime: String,
    @SerialName("client_id")
    val clientId: String
) {
    fun toMap(): Map<String, String> {
        return mapOf(
            "platform" to platform,
            "version" to version,
            "user_id" to userId,
            "creation_time" to creationTime,
            "client_id" to clientId
        )
    }
}
