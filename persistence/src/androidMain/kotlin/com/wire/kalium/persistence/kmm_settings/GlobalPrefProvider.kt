package com.wire.kalium.persistence.kmm_settings

import android.content.Context
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.TokenStorageImpl
import com.wire.kalium.persistence.client.UserConfigStorage
import com.wire.kalium.persistence.client.UserConfigStorageImpl
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorageImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.CoroutineContext


actual class GlobalPrefProvider(context: Context, shouldEncryptData: Boolean = true) {

    private val encryptedSettingsHolder =
        KaliumPreferencesSettings(
            EncryptedSettingsHolder(
                context,
                options = SettingOptions.AppSettings(shouldEncryptData)
            ).encryptedSettings
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val coroutineContext: CoroutineContext =  Dispatchers.IO.limitedParallelism(1)

    actual val authTokenStorage: AuthTokenStorage
        get() = AuthTokenStorage(encryptedSettingsHolder, coroutineContext)
    actual val passphraseStorage: PassphraseStorage
        get() = PassphraseStorageImpl(encryptedSettingsHolder, coroutineContext)
    actual val tokenStorage: TokenStorage
        get() = TokenStorageImpl(encryptedSettingsHolder, coroutineContext)
    actual val userConfigStorage: UserConfigStorage
        get() = UserConfigStorageImpl(encryptedSettingsHolder, coroutineContext)

}
