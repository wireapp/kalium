package com.wire.kalium.persistence.kmmSettings

import android.content.Context
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.TokenStorageImpl
import com.wire.kalium.persistence.config.GlobalAppConfigStorage
import com.wire.kalium.persistence.config.GlobalAppConfigStorageImpl
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorageImpl

actual class GlobalPrefProvider(context: Context, shouldEncryptData: Boolean = true) {

    private val encryptedSettingsHolder: KaliumPreferences = KaliumPreferencesSettings(
        encryptedSettingsBuilder(SettingOptions.AppSettings(shouldEncryptData), EncryptedSettingsPlatformParam(context))
    )

    actual val authTokenStorage: AuthTokenStorage
        get() = AuthTokenStorage(encryptedSettingsHolder)
    actual val passphraseStorage: PassphraseStorage
        get() = PassphraseStorageImpl(encryptedSettingsHolder)
    actual val tokenStorage: TokenStorage
        get() = TokenStorageImpl(encryptedSettingsHolder)
    actual val globalAppConfigStorage: GlobalAppConfigStorage = GlobalAppConfigStorageImpl(encryptedSettingsHolder)

}
