/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

fun BackupMetadata.isWebBackup(): Boolean = platform == "Web"
