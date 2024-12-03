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
@file:OptIn(ExperimentalUnsignedTypes::class)

package com.wire.backup.file.header

import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_MEMLIMIT_MIN
import com.wire.backup.file.header.EncryptionSalt.Encrypted.Companion.SALT_SIZE_IN_BYTES
import okio.Buffer

/**
 * The unencrypted data we write on the beginning of the backup files.
 *
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal data class BackupHeader(
    val version: Int,
    val isEncrypted: Boolean,
    val hashData: HashData,
    val chaCha20Header: UByteArray
) {

    private val extraGap = byteArrayOf(0x00)

    fun toByteArray(): ByteArray {
        val buffer = Buffer()
        buffer.write(format.encodeToByteArray())
        buffer.write(extraGap)
        buffer.writeShort(version.toInt())
        buffer.write(encryptionSalt.toByteArray())
        buffer.write(hashedUserId.toByteArray())
        buffer.writeInt(operationsLimit)
        buffer.writeInt(hashingMemoryLimit)

        return buffer.readByteArray()
    }

    enum class HeaderDecodingErrors {
        INVALID_USER_ID, INVALID_VERSION, INVALID_FORMAT
    }
}

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
    val operationsLimit: UInt,

    /**
     * Memory used by the hashing algorithm.
     * See [Libsodium's Documentation](https://libsodium.gitbook.io/doc/password_hashing/default_phf#key-derivation).
     * This value has to be bigger than [crypto_pwhash_MEMLIMIT_MIN].
     */
    val hashingMemoryLimit: UInt
) {
    init {
        require(hashedUserId.size == HASHED_USER_ID_SIZE_IN_BYTES.toInt()) {
            "Hashed user ID has to be $HASHED_USER_ID_SIZE_IN_BYTES bytes!"
        }
        require(salt.size == SALT_SIZE_IN_BYTES.toInt()) { "Salt has to be $SALT_SIZE_IN_BYTES bytes!" }
    }

    companion object {
        const val HASHED_USER_ID_SIZE_IN_BYTES = 32L
        const val SALT_SIZE_IN_BYTES = 16L
    }
}
