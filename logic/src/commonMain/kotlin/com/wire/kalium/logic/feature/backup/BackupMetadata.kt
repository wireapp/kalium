/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
    val clientId: String?,
    @SerialName("user_database_passphrase")
    val userDBPassphrase: String?,
    @SerialName("is_user_db_sql_ciphered")
    val isUserDBSQLCiphered: Boolean?
) {
    override fun toString(): String = Json.encodeToString(this)
}

@Serializable
data class BackupWebMetadata(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("creation_time")
    val creationTime: String,
    @SerialName("platform")
    val platform: String,
    @SerialName("user_handle")
    val userHandle: String?,
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_name")
    val userName: String,
    @SerialName("version")
    val version: String,

) {
    override fun toString(): String = Json.encodeToString(this)
}
