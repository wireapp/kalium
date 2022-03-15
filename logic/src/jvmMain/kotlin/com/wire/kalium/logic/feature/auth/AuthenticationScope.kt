package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder

actual class AuthenticationScope(
    private val rootDir: String,
    clientLabel: String,
    sessionRepository: SessionRepository
) : AuthenticationScopeCommon(clientLabel, sessionRepository) {
    private val path: String get() = String.format("%s/preferences", rootDir)

    override val encryptedSettingsHolder: EncryptedSettingsHolder
        get() = EncryptedSettingsHolder(path)
}
