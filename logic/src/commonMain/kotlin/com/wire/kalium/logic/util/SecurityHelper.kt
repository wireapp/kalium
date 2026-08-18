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
import com.wire.kalium.cryptography.utils.CryptoServiceInfo
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.db.GlobalDatabaseSecret
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import kotlin.io.encoding.Base64

internal expect class SecureRandom internal constructor() {
    fun nextBytes(length: Int): ByteArray
    fun nextInt(bound: Int): Int

    /**
     * Which security provider serves this randomness, for the security providers debug screen.
     *
     * Null on platforms with no security provider registry.
     */
    fun serviceInfo(): CryptoServiceInfo?
}

internal interface SecurityHelper {
    fun globalDBKeyMaterial(databaseExists: Boolean): GlobalDBKeyMaterial
    fun clearGlobalDBLegacyKey()
    fun userDBSecret(userId: UserId, databaseExists: Boolean): UserDBSecret
    fun userDBOrSecretNull(userId: UserId): UserDBSecret?
    fun setDBPassphrase(key: String, passphrase: String)
    suspend fun mlsDBSecret(userId: UserId, rootDir: String): MlsDBSecret
    suspend fun proteusDBSecret(userId: UserId, rootDir: String): ProteusDBSecret
}

/**
 * The key the global database should end up on, plus the key it may still be encrypted with.
 *
 * [legacyKey] is non-null only while the legacy alias is still stored, which is exactly the window
 * in which a migration can be outstanding. Once it is cleared, [rawKey] is the whole story.
 *
 * Not a data class on purpose: both fields are [ByteArray]s, so generated `equals`/`hashCode` would
 * compare by identity and quietly lie about equality.
 */
internal class GlobalDBKeyMaterial(
    val rawKey: GlobalDatabaseSecret,
    val legacyKey: ByteArray? = null
)

internal typealias DatabaseMigrator = suspend (rootDir: String, oldKey: String, passphrase: ByteArray) -> Unit

@Suppress("TooManyFunctions")
internal class SecurityHelperImpl(
    private val passphraseStorage: PassphraseStorage,
    private val databaseMigrator: DatabaseMigrator = ::migrateDatabaseKey
) : SecurityHelper {

    /**
     * The raw key is generated and stored *before* it is ever used, so a crash between storing it
     * and rekeying the database only costs an unused alias. The reverse order would lose the
     * database.
     */
    override fun globalDBKeyMaterial(databaseExists: Boolean): GlobalDBKeyMaterial {
        val rawKey = getOrGeneratePassPhrase(GLOBAL_DB_PASSPHRASE_ALIAS_V2).toPreservedByteArray.toSqlCipherRawKey()
        val legacyKey = if (databaseExists) getStoredDbPassword(GLOBAL_DB_PASSPHRASE_ALIAS)?.toPreservedByteArray else null
        return GlobalDBKeyMaterial(GlobalDatabaseSecret(rawKey), legacyKey)
    }

    override fun clearGlobalDBLegacyKey() {
        if (getStoredDbPassword(GLOBAL_DB_PASSPHRASE_ALIAS) != null) {
            passphraseStorage.clearPassphrase(GLOBAL_DB_PASSPHRASE_ALIAS)
        }
    }

    override fun userDBSecret(userId: UserId, databaseExists: Boolean): UserDBSecret {
        val v2Alias = "${USER_DB_PASSPHRASE_PREFIX_V2}_$userId"
        return getStoredDbPassword(v2Alias)?.let {
            UserDBSecret(it.toPreservedByteArray.toSqlCipherRawKey())
        } ?: if (databaseExists) {
            // Existing user databases keep their legacy key: rekeying them is not worth the cost.
            UserDBSecret(getOrGeneratePassPhrase("${USER_DB_PASSPHRASE_PREFIX}_$userId").toPreservedByteArray)
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

    /**
     * Converts 32 bytes of key material to SQLCipher's raw-key representation, `x'<64 hex chars>'`.
     *
     * SQLCipher only recognises this form when the payload is exactly `keySize * 2 + 3` bytes, so
     * the size is asserted rather than padded or truncated.
     */
    private fun ByteArray.toSqlCipherRawKey(): ByteArray {
        require(size == SQLCIPHER_RAW_KEY_BYTES) {
            "SQLCipher raw keys must contain exactly $SQLCIPHER_RAW_KEY_BYTES bytes, but got $size"
        }

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
        const val SQLCIPHER_RAW_KEY_BYTES = 32
        const val SQLCIPHER_RAW_KEY_MARKER: Byte = 0x78
        const val SQLCIPHER_RAW_KEY_PAYLOAD_LENGTH = 67
        const val SQLCIPHER_RAW_KEY_QUOTE: Byte = 0x27
        const val RAW_KEY_PREFIX_LENGTH = 2
        const val NIBBLE_BITS = 4
        const val LOW_NIBBLE_MASK = 0x0F
        const val UNSIGNED_BYTE_MASK = 0xFF
        const val GLOBAL_DB_PASSPHRASE_ALIAS = "global_db_passphrase_alias"
        const val GLOBAL_DB_PASSPHRASE_ALIAS_V2 = "global_db_passphrase_alias_v2"
        const val USER_DB_PASSPHRASE_PREFIX = "user_db_secret_alias"
        const val USER_DB_PASSPHRASE_PREFIX_V2 = "user_db_secret_alias_v2"
        const val MLS_DB_PASSPHRASE_PREFIX = "mls_db_secret_alias"
        const val MLS_DB_PASSPHRASE_PREFIX_V2 = "mls_db_secret_alias_v2"
        const val PROTEUS_DB_PASSPHRASE_PREFIX = "proteus_db_secret_alias"
        const val PROTEUS_DB_PASSPHRASE_PREFIX_V2 = "proteus_db_secret_alias_v2"
    }
}
