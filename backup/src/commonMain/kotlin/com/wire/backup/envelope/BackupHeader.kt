/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
@file:OptIn(ExperimentalUnsignedTypes::class)

package com.wire.backup.envelope

import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_MEMLIMIT_MIN
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.encryption.XChaChaPoly1305AuthenticationData
import com.wire.backup.envelope.HashData.Companion.HASHED_USER_ID_SIZE_IN_BYTES
import com.wire.backup.envelope.HashData.Companion.SALT_SIZE_IN_BYTES
import com.wire.backup.hash.HASH_MEM_LIMIT
import com.wire.backup.hash.HASH_OPS_LIMIT
import com.wire.backup.hash.hashUserId

/**
 * The unencrypted data we write on the beginning of the backup files.
 *
 */
internal data class BackupHeader(
    val version: Int,
    val isEncrypted: Boolean,
    val hashData: HashData
)

internal data class HashData(
    /**
     * The hashed ID of the user that originally created this backup.
     * This hash is calculated using Argon2, with this [salt], [operationsLimit] and [hashingMemoryLimit].
     * This array is [HASHED_USER_ID_SIZE_IN_BYTES] long.
     */
    val hashedUserId: UByteArray,

    /**
     * The salt used in order to create the [hashedUserId] and used to derivate the encryption password to read/write the encrypted archive.
     * This array is [SALT_SIZE_IN_BYTES] long.
     */
    val salt: UByteArray,

    /**
     * Represents the maximum amount of computations to perform.
     * Raising this number will make the function require more CPU cycles to compute a key.
     * See [Libsodium's Documentation](https://libsodium.gitbook.io/doc/password_hashing/default_phf#key-derivation).
     */
    val operationsLimit: ULong,

    /**
     * Memory used by the hashing algorithm.
     * See [Libsodium's Documentation](https://libsodium.gitbook.io/doc/password_hashing/default_phf#key-derivation).
     * This value has to be bigger than [crypto_pwhash_MEMLIMIT_MIN].
     */
    val hashingMemoryLimit: Int
) {
    init {
        require(hashedUserId.size == HASHED_USER_ID_SIZE_IN_BYTES) {
            "Hashed user ID has to be $HASHED_USER_ID_SIZE_IN_BYTES bytes long!"
        }
        require(salt.size == SALT_SIZE_IN_BYTES) { "Salt has to be $SALT_SIZE_IN_BYTES bytes long!" }
        require(hashingMemoryLimit >= MINIMUM_MEMORY_LIMIT) {
            "Memory Limit must be equal to or bigger than $MINIMUM_MEMORY_LIMIT!"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as HashData

        if (!hashedUserId.contentEquals(other.hashedUserId)) return false
        if (!salt.contentEquals(other.salt)) return false
        if (operationsLimit != other.operationsLimit) return false
        if (hashingMemoryLimit != other.hashingMemoryLimit) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hashedUserId.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + operationsLimit.hashCode()
        result = 31 * result + hashingMemoryLimit.hashCode()
        return result
    }

    companion object {
        const val HASHED_USER_ID_SIZE_IN_BYTES = 32
        const val SALT_SIZE_IN_BYTES = 16
        const val MINIMUM_MEMORY_LIMIT = crypto_pwhash_MEMLIMIT_MIN

        suspend fun defaultFromUserId(
            userId: BackupQualifiedId,
        ): HashData {
            val salt = XChaChaPoly1305AuthenticationData.newSalt()
            return HashData(hashUserId(userId, salt, HASH_MEM_LIMIT, HASH_OPS_LIMIT), salt, HASH_OPS_LIMIT, HASH_MEM_LIMIT)
        }
    }
}
