package com.wire.kalium.persistence.dbPassphrase

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

interface PassphraseStorage {
        suspend fun getPassphrase(key: String): String?

        suspend fun setPassphrase(key: String, passphrase: String)

        suspend fun clearPassphrase(key: String)
}

class PassphraseStorageImpl internal constructor(
    private val kaliumPreferences: KaliumPreferences,
    private val coroutineContext: CoroutineContext
): PassphraseStorage {
    override suspend fun getPassphrase(key: String): String? = withContext(coroutineContext) {
        kaliumPreferences.getString(key)
    }

    override suspend fun setPassphrase(key: String, passphrase: String) = withContext(coroutineContext) {
        kaliumPreferences.putString(key, passphrase)
    }

    override suspend fun clearPassphrase(key: String) = withContext(coroutineContext) {
        kaliumPreferences.remove(key)
    }
}
