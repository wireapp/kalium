package com.wire.kalium.logic.feature

import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.configuration.ClientConfigImpl
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.PlatformUserStorageProperties
import com.wire.kalium.logic.di.UserStorageProvider
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider

@Suppress("LongParameterList")
fun UserSessionScope(
    platformUserStorageProperties: PlatformUserStorageProperties,
    userId: UserId,
    authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    globalScope: GlobalKaliumScope,
    globalCallManager: GlobalCallManager,
    globalPreferences: GlobalPrefProvider,
    dataStoragePaths: DataStoragePaths,
    kaliumConfigs: KaliumConfigs,
    featureSupport: FeatureSupport,
    userStorageProvider: UserStorageProvider,
    userSessionScopeProvider: UserSessionScopeProvider,
) : UserSessionScope {

    val clientConfig: ClientConfig = ClientConfigImpl()

    return UserSessionScope(
        userId,
        authenticatedDataSourceSet,
        globalScope,
        globalCallManager,
        globalPreferences,
        dataStoragePaths,
        kaliumConfigs,
        featureSupport,
        userSessionScopeProvider,
        userStorageProvider,
        clientConfig,
        platformUserStorageProperties
    )
}
