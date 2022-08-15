package com.wire.kalium.logic.feature

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences


@Suppress("LongParameterList")
actual class UserSessionScope(
    userId: UserId,
    authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    sessionRepository: SessionRepository,
    globalCallManager: GlobalCallManager,
    globalPreferences: KaliumPreferences,
    dataStoragePaths: DataStoragePaths,
    kaliumConfigs: KaliumConfigs
) : UserSessionScopeCommon(
    userId,
    authenticatedDataSourceSet,
    sessionRepository,
    globalCallManager,
    globalPreferences,
    dataStoragePaths,
    kaliumConfigs
) {
    override val clientConfig: ClientConfig get() = ClientConfig()

    init {
        onInit()
    }
}
