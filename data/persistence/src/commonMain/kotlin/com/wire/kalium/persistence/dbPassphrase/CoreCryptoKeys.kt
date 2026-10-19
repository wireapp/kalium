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

package com.wire.kalium.persistence.dbPassphrase

import kotlin.io.encoding.Base64

const val PROTEUS_DB_PASSPHRASE_PREFIX_V2 = "proteus_db_secret_alias_v2"
const val MLS_DB_PASSPHRASE_PREFIX_V2 = "mls_db_secret_alias_v2"

/** Read the current key only. Missing, malformed or legacy keys never trigger generation or migration. */
fun PassphraseStorage.existingCoreCryptoKey(alias: String): ByteArray? {
    val encoded = getPassphrase(alias) ?: return null
    return try {
        Base64.decode(encoded).takeIf { it.size == CORE_CRYPTO_KEY_BYTES }
    } catch (_: IllegalArgumentException) {
        null
    }
}

private const val CORE_CRYPTO_KEY_BYTES = 32
