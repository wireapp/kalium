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

package com.wire.kalium.logic.util

import com.wire.kalium.cryptography.MlsDBSecret
import com.wire.kalium.cryptography.ProteusDBSecret
import com.wire.kalium.cryptography.migrateDatabaseKey
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.db.GlobalDatabaseSecret
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import kotlin.io.encoding.Base64

internal expect class SecureRandom internal constructor() {
    fun nextBytes(length: Int): ByteArray
    fun nextInt(bound: Int): Int
}

internal interface SecurityHelper {
    fun globalDBSecret(): GlobalDatabaseSecret
    fun globalDBKeyMaterial(databaseExists: Boolean): GlobalDBKeyMaterial
    fun userDBSecret(userId: UserId): UserDBSecret
    fun userDBSecret(userId: UserId, databaseExists: Boolean): UserDBSecret
    fun userDBOrSecretNull(userId: UserId): UserDBSecret?
    fun markGlobalDBSecretAsV2()
    fun setDBPassphrase(key: String, passphrase: String)
    suspend fun mlsDBSecret(userId: UserId, rootDir: String): MlsDBSecret
    suspend fun proteusDBSecret(userId: UserId, rootDir: String): ProteusDBSecret
}

internal data class GlobalDBKeyMaterial(
    val currentSecret: GlobalDatabaseSecret,
    val migrationRawKey: ByteArray? = null
)

internal typealias DatabaseMigrator = suspend (rootDir: String, oldKey: String, passphrase: ByteArray) -> Unit

@Suppress("TooManyFunctions")
internal class SecurityHelperImpl(
    private val passphraseStorage: PassphraseStorage,
    private val databaseMigrator: DatabaseMigrator = ::migrateDatabaseKey
) : SecurityHelper {

    override fun globalDBSecret(): GlobalDatabaseSecret =
        GlobalDatabaseSecret(getOrGeneratePassPhrase(GLOBAL_DB_PASSPHRASE_ALIAS).toPreservedByteArray)

    override fun globalDBKeyMaterial(databaseExists: Boolean): GlobalDBKeyMaterial =
        getStoredDbPassword(GLOBAL_DB_PASSPHRASE_ALIAS_V2)?.let {
            if (getStoredDbPassword(GLOBAL_DB_PASSPHRASE_ALIAS_V2_PENDING) != null) {
                passphraseStorage.clearPassphrase(GLOBAL_DB_PASSPHRASE_ALIAS_V2_PENDING)
            }
            GlobalDBKeyMaterial(GlobalDatabaseSecret(it.toPreservedByteArray.toSqlCipherRawKey()))
        } ?: if (databaseExists) {
            val pendingV2Secret = getOrGeneratePassPhrase(GLOBAL_DB_PASSPHRASE_ALIAS_V2_PENDING)
                .toPreservedByteArray
                .toSqlCipherRawKey()
            GlobalDBKeyMaterial(
                currentSecret = globalDBSecret(),
                migrationRawKey = pendingV2Secret
            )
        } else {
            GlobalDBKeyMaterial(
                GlobalDatabaseSecret(
                    getOrGeneratePassPhrase(GLOBAL_DB_PASSPHRASE_ALIAS_V2).toPreservedByteArray.toSqlCipherRawKey()
                )
            )
        }

    override fun userDBSecret(userId: UserId): UserDBSecret =
        UserDBSecret(getOrGeneratePassPhrase("${USER_DB_PASSPHRASE_PREFIX}_$userId").toPreservedByteArray)

    override fun userDBSecret(userId: UserId, databaseExists: Boolean): UserDBSecret {
        val v2Alias = "${USER_DB_PASSPHRASE_PREFIX_V2}_$userId"
        return getStoredDbPassword(v2Alias)?.let {
            UserDBSecret(it.toPreservedByteArray.toSqlCipherRawKey())
        } ?: if (databaseExists) {
            userDBSecret(userId)
        } else {
            UserDBSecret(getOrGeneratePassPhrase(v2Alias).toPreservedByteArray.toSqlCipherRawKey())
        }
    }

    override fun userDBOrSecretNull(userId: UserId): UserDBSecret? =
        getStoredDbPassword("${USER_DB_PASSPHRASE_PREFIX_V2}_$userId")?.let {
            UserDBSecret(it.toPreservedByteArray.toSqlCipherRawKey())
        } ?: getStoredDbPassword("${USER_DB_PASSPHRASE_PREFIX}_$userId")?.toPreservedByteArray?.let {
            UserDBSecret(it)
        }

    @Suppress("ReturnCount")
    override suspend fun mlsDBSecret(userId: UserId, rootDir: String): MlsDBSecret {
        // Step 1: Try current format (v2) - return if found
        getStoredDbPassword("${MLS_DB_PASSPHRASE_PREFIX_V2}_$userId")
            ?.let { return MlsDBSecret(Base64.decode(it)) }

        // Step 2: Try legacy format (v1) - migrate to v2 if found
        getStoredDbPassword("${MLS_DB_PASSPHRASE_PREFIX}_$userId")
            ?.let { legacyPassphrase ->
                return SecureRandom().nextBytes(MIN_DATABASE_SECRET_LENGTH).also { newKeyBytes ->
                    databaseMigrator(rootDir, legacyPassphrase, newKeyBytes)
                    passphraseStorage.setPassphrase("${MLS_DB_PASSPHRASE_PREFIX_V2}_$userId", Base64.encode(newKeyBytes))
                }.let { MlsDBSecret(it) }
            }

        // Step 3: Generate new secret as fallback
        return getOrGeneratePassPhrase("${MLS_DB_PASSPHRASE_PREFIX_V2}_$userId").let {
            MlsDBSecret(Base64.decode(it))
        }
    }

    @Suppress("ReturnCount")
    override suspend fun proteusDBSecret(userId: UserId, rootDir: String): ProteusDBSecret {
            // Step 1: Try current format (v2) - return if found
            getStoredDbPassword("${PROTEUS_DB_PASSPHRASE_PREFIX_V2}_$userId")
                ?.let { return ProteusDBSecret(Base64.decode(it)) }

            // Step 2: Try legacy format (v1) - migrate to v2 if found
            getStoredDbPassword("${PROTEUS_DB_PASSPHRASE_PREFIX}_$userId")
                ?.let { legacyPassphrase ->
                    return SecureRandom().nextBytes(MIN_DATABASE_SECRET_LENGTH).also { newKeyBytes ->
                        databaseMigrator(rootDir, legacyPassphrase, newKeyBytes)
                        passphraseStorage.setPassphrase("${PROTEUS_DB_PASSPHRASE_PREFIX_V2}_$userId", Base64.encode(newKeyBytes))
                    }.let { ProteusDBSecret(it) }
                }

            // Step 3: Generate new secret as fallback
            return getOrGeneratePassPhrase("${PROTEUS_DB_PASSPHRASE_PREFIX_V2}_$userId").let {
                ProteusDBSecret(Base64.decode(it))
            }
        }

    override fun setDBPassphrase(key: String, passphrase: String) {
        passphraseStorage.setPassphrase(key, passphrase)
    }

    override fun markGlobalDBSecretAsV2() {
        if (getStoredDbPassword(GLOBAL_DB_PASSPHRASE_ALIAS_V2) != null) return

        val pendingV2Secret = getStoredDbPassword(GLOBAL_DB_PASSPHRASE_ALIAS_V2_PENDING)
            ?: error("Cannot mark the global database key as V2 without its pending V2 secret")
        passphraseStorage.setPassphrase(GLOBAL_DB_PASSPHRASE_ALIAS_V2, pendingV2Secret)
        passphraseStorage.clearPassphrase(GLOBAL_DB_PASSPHRASE_ALIAS_V2_PENDING)
    }

    private fun getOrGeneratePassPhrase(alias: String): String =
        getStoredDbPassword(alias) ?: storeDbPassword(alias, generatePassword())

    private fun getStoredDbPassword(passwordAlias: String): String? =
        passphraseStorage.getPassphrase(passwordAlias)

    private fun storeDbPassword(alias: String, keyBytes: ByteArray): String {
        val key = keyBytes.toPreservedString
        passphraseStorage.setPassphrase(alias, key)
        return key
    }

    private fun generatePassword(minPasswordLength: Int = MIN_DATABASE_SECRET_LENGTH): ByteArray {
        val secureRandom = SecureRandom()
        return secureRandom.nextBytes(minPasswordLength)
    }

    private val String.toPreservedByteArray: ByteArray
        get() = Base64.decode(this)

    private val ByteArray.toPreservedString: String
        get() = Base64.encode(this)

    private fun ByteArray.toSqlCipherRawKey(): ByteArray {
        require(size == MIN_DATABASE_SECRET_LENGTH)

        val hexDigits = HEX_DIGITS.encodeToByteArray()
        val result = ByteArray(SQLCIPHER_RAW_KEY_PAYLOAD_LENGTH)
        result[0] = SQLCIPHER_RAW_KEY_MARKER
        result[1] = SQLCIPHER_RAW_KEY_QUOTE

        var sourceIndex = 0
        var destinationIndex = RAW_KEY_PREFIX_LENGTH
        while (sourceIndex < size) {
            val unsignedByte = this[sourceIndex].toInt() and UNSIGNED_BYTE_MASK
            result[destinationIndex++] = hexDigits[unsignedByte ushr NIBBLE_BITS]
            result[destinationIndex++] = hexDigits[unsignedByte and LOW_NIBBLE_MASK]
            sourceIndex++
        }

        result[destinationIndex] = SQLCIPHER_RAW_KEY_QUOTE
        return result
    }

    private companion object {
        const val HEX_DIGITS = "0123456789abcdef"
        const val MIN_DATABASE_SECRET_LENGTH = 32
        const val SQLCIPHER_RAW_KEY_MARKER: Byte = 0x78
        const val SQLCIPHER_RAW_KEY_PAYLOAD_LENGTH = 67
        const val SQLCIPHER_RAW_KEY_QUOTE: Byte = 0x27
        const val RAW_KEY_PREFIX_LENGTH = 2
        const val NIBBLE_BITS = 4
        const val LOW_NIBBLE_MASK = 0x0F
        const val UNSIGNED_BYTE_MASK = 0xFF
        const val GLOBAL_DB_PASSPHRASE_ALIAS = "global_db_passphrase_alias"
        const val GLOBAL_DB_PASSPHRASE_ALIAS_V2 = "global_db_passphrase_alias_v2"
        const val GLOBAL_DB_PASSPHRASE_ALIAS_V2_PENDING = "global_db_passphrase_alias_v2_pending"
        const val USER_DB_PASSPHRASE_PREFIX = "user_db_secret_alias"
        const val USER_DB_PASSPHRASE_PREFIX_V2 = "user_db_secret_alias_v2"
        const val MLS_DB_PASSPHRASE_PREFIX = "mls_db_secret_alias"
        const val MLS_DB_PASSPHRASE_PREFIX_V2 = "mls_db_secret_alias_v2"
        const val PROTEUS_DB_PASSPHRASE_PREFIX = "proteus_db_secret_alias"
        const val PROTEUS_DB_PASSPHRASE_PREFIX_V2 = "proteus_db_secret_alias_v2"
    }
}
