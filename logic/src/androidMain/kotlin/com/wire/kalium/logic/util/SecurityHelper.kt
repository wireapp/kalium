package com.wire.kalium.logic.util

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

internal expect class SecureRandom constructor() {
    fun nextBytes(length: Int): ByteArray
    fun nextInt(bound: Int): Int
}

class SecurityHelper(private val kaliumPreferences: KaliumPreferences) {

    fun passPhrase(alias: String): ByteArray = getStoredDbPassword(alias) ?: storeDbPassword(alias, generatePassword())

    private fun getStoredDbPassword(passwordAlias: String): ByteArray? =
        kaliumPreferences.getString(passwordAlias)?.toPreservedByteArray

    private fun storeDbPassword(alias: String, keyBytes: ByteArray): ByteArray {
        kaliumPreferences.putString(alias, keyBytes.toPreservedString)
        return keyBytes
    }

    private fun generatePassword(): ByteArray {
        val secureRandom = SecureRandom()
        val max = MAX_DATABASE_SECRET_LENGTH
        val min = MIN_DATABASE_SECRET_LENGTH
        val passwordLen = secureRandom.nextInt(max - min + 1) + min
        return secureRandom.nextBytes(passwordLen)
    }

    private val String.toPreservedByteArray: ByteArray
        get() = Base64.decode(this)

    private val ByteArray.toPreservedString: String
        get() = Base64.encode(this)

    companion object {
        const val MAX_DATABASE_SECRET_LENGTH = 48
        const val MIN_DATABASE_SECRET_LENGTH = 32
    }
}
