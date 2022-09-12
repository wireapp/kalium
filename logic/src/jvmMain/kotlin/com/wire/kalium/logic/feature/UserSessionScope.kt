package com.wire.kalium.logic.feature

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.configuration.ClientConfigImpl
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

@Suppress("LongParameterList")
actual class UserSessionScope internal constructor(
    userId: UserId,
    authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    globalCallManager: GlobalCallManager,
    globalPreferences: KaliumPreferences,
    dataStoragePaths: DataStoragePaths,
    kaliumConfigs: KaliumConfigs,
    userSessionScopeProvider: UserSessionScopeProvider,
    serverConfigRepository: Lazy<ServerConfigRepository>,
    globalScope: GlobalKaliumScope
) : UserSessionScopeCommon(
    userId,
    authenticatedDataSourceSet,
    globalCallManager,
    globalPreferences,
    dataStoragePaths,
    kaliumConfigs,
    userSessionScopeProvider,
    serverConfigRepository,
    globalScope
) {
    override val clientConfig: ClientConfig get() = ClientConfigImpl()

    init {
        onInit()
    }
}
