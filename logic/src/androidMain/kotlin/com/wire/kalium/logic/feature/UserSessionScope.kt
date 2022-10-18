package com.wire.kalium.logic.feature

import android.content.Context
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
import com.wire.kalium.logic.util.SecurityHelper
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from UserSessionScopeCommon
 */
@Suppress("LongParameterList")
actual class UserSessionScope internal constructor(
    private val applicationContext: Context,
    userId: UserId,
    authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    globalScope: GlobalKaliumScope,
    globalCallManager: GlobalCallManager,
    globalPreferences: GlobalPrefProvider,
    dataStoragePaths: DataStoragePaths,
    kaliumConfigs: KaliumConfigs,
    featureSupport: FeatureSupport,
    userStorageProvider: UserStorageProvider,
    userSessionScopeProvider: UserSessionScopeProvider
) : UserSessionScopeCommon(
    userId,
    authenticatedDataSourceSet,
    globalScope,
    globalCallManager,
    globalPreferences,
    dataStoragePaths,
    kaliumConfigs,
    featureSupport,
    userSessionScopeProvider,
    userStorageProvider
) {
    override val platformUserStorageProperties: PlatformUserStorageProperties
        get() = PlatformUserStorageProperties(applicationContext, SecurityHelper(globalPreferences.passphraseStorage))

    override val clientConfig: ClientConfig get() = ClientConfigImpl(applicationContext)

    init {
        onInit()
    }
}
