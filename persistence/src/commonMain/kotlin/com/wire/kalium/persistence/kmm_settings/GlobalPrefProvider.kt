package com.wire.kalium.persistence.kmm_settings

import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.UserConfigStorage
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage

expect class GlobalPrefProvider {
    val authTokenStorage: AuthTokenStorage
    val passphraseStorage: PassphraseStorage
    val tokenStorage: TokenStorage

    // TODO: Remove this fpr proper user config storage
    val userConfigStorage: UserConfigStorage
}
