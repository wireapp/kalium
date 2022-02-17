package com.wire.kalium.logic.feature

import android.content.Context
import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.persistence.db.Database
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from UserSessionScopeCommon
 */
actual class UserSessionScope(
    private val applicationContext: Context,
    private val session: AuthSession,
    authenticatedDataSourceSet: AuthenticatedDataSourceSet,
) : UserSessionScopeCommon(session, authenticatedDataSourceSet) {

    override val clientConfig: ClientConfig get() = ClientConfig(applicationContext)
    override val database: Database get() = Database(applicationContext, "main.db", userPreferencesSettings)
    override val encryptedSettingsHolder: EncryptedSettingsHolder
        get() = EncryptedSettingsHolder(applicationContext, "$PREFERENCE_FILE_PREFIX-${session.userId}")

    override val protoContentMapper: ProtoContentMapper = ProtoContentMapper()

    private companion object {
        private const val PREFERENCE_FILE_PREFIX = "user-pref"
    }
}
