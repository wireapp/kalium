package com.wire.kalium.logic.feature.auth

import android.content.Context
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from AuthenticationScopeCommon
 */
actual class AuthenticationScope(
    clientLabel: String,
    sessionRepository: SessionRepository,
    private val applicationContext: Context
) : AuthenticationScopeCommon(clientLabel, sessionRepository) {
    override val encryptedSettingsHolder: EncryptedSettingsHolder
        get() = EncryptedSettingsHolder(applicationContext, PREFERENCE_FILE_NAME)

    private companion object {
        private const val PREFERENCE_FILE_NAME = "app-preference"
    }
}
