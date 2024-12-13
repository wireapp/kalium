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
package com.wire.backup.hash

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_ALG_DEFAULT
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.encryption.initializeLibSodiumIfNeeded

internal const val USER_ID_HASH_SIZE = 32

// crypto_pwhash_argon2i_OPSLIMIT_INTERACTIVE
internal const val HASH_OPS_LIMIT = 4UL

// crypto_pwhash_argon2i_MEMLIMIT_INTERACTIVE
internal const val HASH_MEM_LIMIT = 33554432

internal suspend fun hashUserId(
    qualifiedUserId: BackupQualifiedId,
    salt: UByteArray,
    memoryLimit: Int,
    opsLimit: ULong
): UByteArray {
    initializeLibSodiumIfNeeded()
    return PasswordHash.pwhash(
        USER_ID_HASH_SIZE,
        qualifiedUserId.toString(),
        salt,
        opsLimit,
        memoryLimit,
        crypto_pwhash_ALG_DEFAULT
    )
}
