package com.wire.kalium.persistence.kmm_settings

import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.TokenStorageImpl
import com.wire.kalium.persistence.client.UserConfigStorage
import com.wire.kalium.persistence.client.UserConfigStorageImpl
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorageImpl

actual class GlobalPrefProvider(
    rootPath: String,
    shouldEncryptData: Boolean = true
) {
    private val kaliumPref =
        KaliumPreferencesSettings(EncryptedSettingsHolder(rootPath, SettingOptions.AppSettings(shouldEncryptData)).encryptedSettings)

    actual val authTokenStorage: AuthTokenStorage
        get() = AuthTokenStorage(kaliumPref)
    actual val passphraseStorage: PassphraseStorage
        get() = PassphraseStorageImpl(kaliumPref)
    actual val tokenStorage: TokenStorage
        get() = TokenStorageImpl(kaliumPref)
    actual val userConfigStorage: UserConfigStorage = UserConfigStorageImpl(kaliumPref)
}
