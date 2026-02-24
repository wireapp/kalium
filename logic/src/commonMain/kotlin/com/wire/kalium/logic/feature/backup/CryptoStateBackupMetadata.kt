/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

@Serializable
internal data class CryptoStateBackupMetadata(
    @SerialName("version")
    val version: String,
    @SerialName("client_id")
    val clientId: String,
    @SerialName("mls_db_passphrase")
    val mlsDbPassphrase: String
) {
    internal companion object {
        const val CURRENT_VERSION: String = "1"
    }
}
