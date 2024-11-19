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
package com.wire.backup.file

import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_MEMLIMIT_MIN
import okio.Buffer

/**
 * The unencrypted data we write on the beginning of the backup files.
 *
 */
@OptIn(ExperimentalUnsignedTypes::class)
data class BackupHeader(
    val format: String,
    val version: String,
    val salt: UByteArray,
    val hashedUserId: UByteArray,
    /**
     * Represents the maximum amount of computations to perform. Raising this number will make the function require more CPU cycles to compute a key.
     * See [Libsodium's Documentation](https://libsodium.gitbook.io/doc/password_hashing/default_phf#key-derivation).
     */
    val operationsLimit: Int,
    /**
     * Memory used by the hashing algorithm.
     * See [Libsodium's Documentation](https://libsodium.gitbook.io/doc/password_hashing/default_phf#key-derivation).
     * This value has to be bigger than [crypto_pwhash_MEMLIMIT_MIN].
     */
    val hashingMemoryLimit: Int
) {

    private val extraGap = byteArrayOf(0x00)

    fun toByteArray(): ByteArray {
        val buffer = Buffer()
        buffer.write(format.encodeToByteArray())
        buffer.write(extraGap)
        buffer.write(version.encodeToByteArray())
        buffer.write(salt.toByteArray())
        buffer.write(hashedUserId.toByteArray())
        buffer.writeInt(operationsLimit)
        buffer.writeInt(hashingMemoryLimit)

        return buffer.readByteArray()
    }

    enum class HeaderDecodingErrors {
        INVALID_USER_ID, INVALID_VERSION, INVALID_FORMAT
    }
}
