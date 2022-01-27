package com.wire.kalium.logic.feature.auth

import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder

actual class AuthenticationScope(
    private val rootDir: String,
    loginNetworkContainer: LoginNetworkContainer,
    clientLabel: String
) : AuthenticationScopeCommon(loginNetworkContainer, clientLabel) {
    private val path: String by lazy { String.format("%s/preferences", rootDir) }
    override val encryptedSettingsHolder: EncryptedSettingsHolder
        get() = EncryptedSettingsHolder(path)
}
