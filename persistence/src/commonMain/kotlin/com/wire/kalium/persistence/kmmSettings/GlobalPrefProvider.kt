package com.wire.kalium.persistence.kmmSettings

import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.client.ProxyCredentialsStorage
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.config.GlobalAppConfigStorage
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage

expect class GlobalPrefProvider {
    val authTokenStorage: AuthTokenStorage
    val passphraseStorage: PassphraseStorage
    val tokenStorage: TokenStorage
    val globalAppConfigStorage: GlobalAppConfigStorage
    val proxyCredentialsStorage: ProxyCredentialsStorage
}
