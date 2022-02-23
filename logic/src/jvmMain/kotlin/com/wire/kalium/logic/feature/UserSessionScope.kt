package com.wire.kalium.logic.feature

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.PlatformProtoContentMapper
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.persistence.db.Database
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder

actual class UserSessionScope(
    session: AuthSession,
    authenticatedDataSourceSet: AuthenticatedDataSourceSet,
) : UserSessionScopeCommon(session, authenticatedDataSourceSet) {
    override val clientConfig: ClientConfig get() = ClientConfig()
    override val database: Database
        get() = Database()

    override val encryptedSettingsHolder: EncryptedSettingsHolder = EncryptedSettingsHolder(
        "$PREFERENCE_FILE_PREFIX-${session.userId}"
    )

    override val protoContentMapper: ProtoContentMapper get() = PlatformProtoContentMapper()

    private companion object {
        private const val PREFERENCE_FILE_PREFIX = ".user-pref"
    }
}
