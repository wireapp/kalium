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

import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.logic.data.conversation.ClientId
import kotlinx.datetime.Instant

public data class BackupRootKey(
    val id: String,
    val keyMaterial: ByteArray,
    val createdAt: Instant,
    val createdByClientId: ClientId,
    val version: Int,
) {
    public fun fingerprint(): String =
        calcSHA256(keyMaterial)
            .take(FINGERPRINT_BYTES)
            .joinToString(separator = ":") { byte -> byte.toUByte().toString(radix = 16).padStart(2, '0') }
            .uppercase()

    private companion object {
        const val FINGERPRINT_BYTES = 8
    }
}

public data class BackupRootKeyInfo(
    val id: String,
    val fingerprint: String,
    val createdAt: Instant,
    val createdByClientId: ClientId,
    val version: Int,
)

public fun BackupRootKey.toBackupRootKeyInfo(): BackupRootKeyInfo =
    BackupRootKeyInfo(
        id = id,
        fingerprint = fingerprint(),
        createdAt = createdAt,
        createdByClientId = createdByClientId,
        version = version,
    )
