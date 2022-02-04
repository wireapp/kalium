package com.wire.kalium.logic.feature.auth

import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder

actual class AuthenticationScope(
    private val rootDir: String,
    clientLabel: String
) : AuthenticationScopeCommon(clientLabel) {
    private val path: String get() = String.format("%s/preferences", rootDir)

    override val encryptedSettingsHolder: EncryptedSettingsHolder
        get() = EncryptedSettingsHolder(path)
}
