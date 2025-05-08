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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.db.GlobalDatabaseSecret
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import io.mockative.Mockable

internal expect class SecureRandom constructor() {
    fun nextBytes(length: Int): ByteArray
    fun nextInt(bound: Int): Int
}

@Mockable
internal interface SecurityHelper {
    fun globalDBSecret(): GlobalDatabaseSecret
    fun userDBSecret(userId: UserId): UserDBSecret
    fun userDBOrSecretNull(userId: UserId): UserDBSecret?
    fun mlsDBSecret(userId: UserId): MlsDBSecret
    fun proteusDBSecret(userId: UserId): ProteusDBSecret
}

internal class SecurityHelperImpl(private val passphraseStorage: PassphraseStorage) : SecurityHelper {

    override fun globalDBSecret(): GlobalDatabaseSecret =
        GlobalDatabaseSecret(getOrGeneratePassPhrase(GLOBAL_DB_PASSPHRASE_ALIAS).toPreservedByteArray)

    override fun userDBSecret(userId: UserId): UserDBSecret =
        UserDBSecret(getOrGeneratePassPhrase("${USER_DB_PASSPHRASE_PREFIX}_$userId").toPreservedByteArray)

    override fun userDBOrSecretNull(userId: UserId): UserDBSecret? =
        getStoredDbPassword("${USER_DB_PASSPHRASE_PREFIX}_$userId")?.toPreservedByteArray?.let { UserDBSecret(it) }

    override fun mlsDBSecret(userId: UserId): MlsDBSecret =
        MlsDBSecret(getOrGeneratePassPhrase("${MLS_DB_PASSPHRASE_PREFIX}_$userId"))

    override fun proteusDBSecret(userId: UserId): ProteusDBSecret =
        ProteusDBSecret(getOrGeneratePassPhrase("${PROTEUS_DB_PASSPHRASE_PREFIX}_$userId"))

    private fun getOrGeneratePassPhrase(alias: String): String =
        getStoredDbPassword(alias) ?: storeDbPassword(alias, generatePassword())

    private fun getStoredDbPassword(passwordAlias: String): String? =
        passphraseStorage.getPassphrase(passwordAlias)

    private fun storeDbPassword(alias: String, keyBytes: ByteArray): String {
        val key = keyBytes.toPreservedString
        passphraseStorage.setPassphrase(alias, key)
        return key
    }

    private fun generatePassword(): ByteArray {
        val secureRandom = SecureRandom()
        val max = MAX_DATABASE_SECRET_LENGTH
        val min = MIN_DATABASE_SECRET_LENGTH
        val passwordLen = secureRandom.nextInt(max - min + 1) + min
        return secureRandom.nextBytes(passwordLen)
    }

    private val String.toPreservedByteArray: ByteArray
        get() = this.decodeBase64Bytes()

    private val ByteArray.toPreservedString: String
        get() = this.encodeBase64()

    private companion object {
        const val MAX_DATABASE_SECRET_LENGTH = 48
        const val MIN_DATABASE_SECRET_LENGTH = 32
        const val GLOBAL_DB_PASSPHRASE_ALIAS = "global_db_passphrase_alias"
        const val USER_DB_PASSPHRASE_PREFIX = "user_db_secret_alias"
        const val MLS_DB_PASSPHRASE_PREFIX = "mls_db_secret_alias"
        const val PROTEUS_DB_PASSPHRASE_PREFIX = "proteus_db_secret_alias"
    }
}
