package com.wire.kalium.logic.feature

import android.content.Context
import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.configuration.ClientConfigImpl
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

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
    globalPreferences: KaliumPreferences,
    dataStoragePaths: DataStoragePaths,
    kaliumConfigs: KaliumConfigs,
    userSessionScopeProvider: UserSessionScopeProvider
) : UserSessionScopeCommon(
    userId,
    authenticatedDataSourceSet,
    globalScope,
    globalCallManager,
    globalPreferences,
    dataStoragePaths,
    kaliumConfigs,
    userSessionScopeProvider
) {

    override val clientConfig: ClientConfig get() = ClientConfigImpl(applicationContext)

    init {
        onInit()
    }
}
