package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder

actual class AuthenticationScope(
    private val rootDir: String,
    clientLabel: String,
    kaliumLogger: KaliumLogger
) : AuthenticationScopeCommon(clientLabel, kaliumLogger) {
    private val path: String get() = String.format("%s/preferences", rootDir)

    override val encryptedSettingsHolder: EncryptedSettingsHolder
        get() = EncryptedSettingsHolder(path)
}
