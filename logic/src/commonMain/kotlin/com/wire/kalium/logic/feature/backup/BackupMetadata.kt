package com.wire.kalium.logic.feature.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    val clientId: String?
) {
    override fun toString(): String = Json.encodeToString(this)
}
