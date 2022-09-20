package com.wire.kalium.persistence.dbPassphrase

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

interface PassphraseStorage {
    fun getPassphrase(key: String): String?
    fun setPassphrase(key: String, passphrase: String)
    fun clearPassphrase(key: String)
}

class PassphraseStorageImpl internal constructor(
    private val kaliumPreferences: KaliumPreferences
) : PassphraseStorage {
    override fun getPassphrase(key: String): String? = kaliumPreferences.getString(key)

    override fun setPassphrase(key: String, passphrase: String) {
        kaliumPreferences.putString(key, passphrase)
    }

    override fun clearPassphrase(key: String) {
        kaliumPreferences.remove(key)
    }
}
