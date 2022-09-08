package com.wire.kalium.logic.util

import com.wire.kalium.cryptography.MlsDBSecret
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.persistence.db.GlobalDatabaseSecret
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64

internal expect class SecureRandom constructor() {
    fun nextBytes(length: Int): ByteArray
    fun nextInt(bound: Int): Int
}

internal class SecurityHelper(private val kaliumPreferences: KaliumPreferences) {

    fun globalDBSecret(): GlobalDatabaseSecret =
        GlobalDatabaseSecret(getOrGeneratePassPhrase(GLOBAL_DB_PASSPHRASE_ALIAS).toPreservedByteArray)

    fun userDBSecret(userId: UserId): UserDBSecret =
        UserDBSecret(getOrGeneratePassPhrase("${USER_DB_PASSPHRASE_PREFIX}_$userId").toPreservedByteArray)

    fun mlsDBSecret(userId: UserId): MlsDBSecret = MlsDBSecret(getOrGeneratePassPhrase("${MLS_DB_PASSPHRASE_PREFIX}_$userId").also { kaliumLogger.i("Generated PassPhrase") })

    private fun getOrGeneratePassPhrase(alias: String): String = getStoredDbPassword(alias) ?: storeDbPassword(alias, generatePassword().also { kaliumLogger.i("password generated: $it") }).also { kaliumLogger.i("store db password: ${it}") }

    private fun getStoredDbPassword(passwordAlias: String): String? = kaliumPreferences.getString(passwordAlias).also { kaliumLogger.i("got stored db password: ${it}") }

    private fun storeDbPassword(alias: String, keyBytes: ByteArray): String {
        val key = keyBytes.toPreservedString
        kaliumPreferences.putString(alias, key)
        return key
    }

    private fun generatePassword(): ByteArray {
        kaliumLogger.i("Generate password")
        val secureRandom = SecureRandom()
        val max = MAX_DATABASE_SECRET_LENGTH
        val min = MIN_DATABASE_SECRET_LENGTH
        val passwordLen = secureRandom.nextInt(max - min + 1) + min
        kaliumLogger.i("Generate password done")
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
    }
}
