package com.wire.kalium.persistence.kmm_settings

import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.UserConfigStorage
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage

actual class GlobalPrefProvider {
    actual val authTokenStorage: AuthTokenStorage
        get() = TODO("Not yet implemented")
    actual val passphraseStorage: PassphraseStorage
        get() = TODO("Not yet implemented")
    actual val tokenStorage: TokenStorage
        get() = TODO("Not yet implemented")
    actual val userConfigStorage: UserConfigStorage
        get() = TODO("Not yet implemented")
}
