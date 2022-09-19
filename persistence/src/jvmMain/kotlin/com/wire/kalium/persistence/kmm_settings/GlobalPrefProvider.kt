package com.wire.kalium.persistence.kmm_settings

import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.TokenStorageImpl
import com.wire.kalium.persistence.client.UserConfigStorage
import com.wire.kalium.persistence.client.UserConfigStorageImpl
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorageImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

actual class GlobalPrefProvider(
    rootPath: String,
    shouldEncryptData: Boolean = true
) {
    private val kaliumPref =
        KaliumPreferencesSettings(EncryptedSettingsHolder(rootPath, SettingOptions.AppSettings(shouldEncryptData)).encryptedSettings)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    private val coroutineContext = Dispatchers.IO.limitedParallelism(1)

    actual val authTokenStorage: AuthTokenStorage
        get() = AuthTokenStorage(kaliumPref, coroutineContext)
    actual val passphraseStorage: PassphraseStorage
        get() = PassphraseStorageImpl(kaliumPref, coroutineContext)
    actual val tokenStorage: TokenStorage
        get() = TokenStorageImpl(kaliumPref, coroutineContext)
    actual val userConfigStorage: UserConfigStorage
        get() = UserConfigStorageImpl(kaliumPref, coroutineContext)
}
