package com.wire.kalium.persistence.kmm_settings

import android.content.Context
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.TokenStorageImpl
import com.wire.kalium.persistence.client.UserConfigStorage
import com.wire.kalium.persistence.client.UserConfigStorageImpl
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorageImpl

actual class GlobalPrefProvider(context: Context, shouldEncryptData: Boolean = true) {

    private val encryptedSettingsHolder =
        KaliumPreferencesSettings(
            EncryptedSettingsHolder(
                context,
                options = SettingOptions.AppSettings(shouldEncryptData)
            ).encryptedSettings
        )

    actual val authTokenStorage: AuthTokenStorage
        get() = AuthTokenStorage(encryptedSettingsHolder)
    actual val passphraseStorage: PassphraseStorage
        get() = PassphraseStorageImpl(encryptedSettingsHolder)
    actual val tokenStorage: TokenStorage
        get() = TokenStorageImpl(encryptedSettingsHolder)
    actual val userConfigStorage: UserConfigStorage
        get() = UserConfigStorageImpl(encryptedSettingsHolder)

}
