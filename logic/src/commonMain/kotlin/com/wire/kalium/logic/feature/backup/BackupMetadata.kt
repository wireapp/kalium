package com.wire.kalium.logic.feature.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

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
    val clientId: String,
    @SerialName("user_database_passphrase")
    val userDBPassphrase: String,
    @SerialName("is_user_db_sqlciphered")
    val isUserDBSQLCiphered: Boolean
) {
    override fun toString(): String = Json.encodeToString(this)
}
