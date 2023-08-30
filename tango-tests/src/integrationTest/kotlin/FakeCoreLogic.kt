/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.CoreLogicCommon
import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.UserSessionScopeProviderImpl
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.logic.network.NetworkStateObserverImpl
import com.wire.kalium.logic.sync.GlobalWorkScheduler
import com.wire.kalium.logic.sync.GlobalWorkSchedulerImpl
import com.wire.kalium.logic.util.PlatformContext
import com.wire.kalium.network.networkContainer.UnboundNetworkContainerCommon
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import kotlinx.coroutines.cancel
import java.io.File

/**
 * @sample samples.logic.CoreLogicSamples.versionedAuthScope
 */
class FakeCoreLogic(
    rootPath: String,
    kaliumConfigs: KaliumConfigs,
    userAgent: String
) : CoreLogic(
    rootPath = rootPath, kaliumConfigs = kaliumConfigs, userAgent = userAgent
) {

    override fun getGlobalScope(): GlobalKaliumScope =
        GlobalKaliumScope(
            userAgent,
            globalDatabase,
            globalPreferences,
            kaliumConfigs,
            userSessionScopeProvider,
            authenticationScopeProvider,
            networkStateObserver,
            unboundNetworkContainer = lazy {
                UnboundNetworkContainerCommon(
                    networkStateObserver,
                    kaliumConfigs.developmentApiEnabled,
                    userAgent,
                    kaliumConfigs.ignoreSSLCertificatesForUnboundCalls,
                    kaliumConfigs.certPinningConfig
                )
            }
        )


    override val globalPreferences: GlobalPrefProvider =
        GlobalPrefProvider(
            rootPath = rootPath,
            shouldEncryptData = kaliumConfigs.shouldEncryptData
        )

    override val globalDatabase: GlobalDatabaseProvider =
        GlobalDatabaseProvider(File("$rootPath/global-storage"))

    override fun getSessionScope(userId: UserId): UserSessionScope =
        userSessionScopeProvider.value.getOrCreate(userId)

    override fun deleteSessionScope(userId: UserId) {
        userSessionScopeProvider.value.get(userId)?.cancel()
        userSessionScopeProvider.value.delete(userId)
    }

    override val networkStateObserver: NetworkStateObserver = NetworkStateObserverImpl()
    override val userSessionScopeProvider: Lazy<UserSessionScopeProvider> = lazy {
        UserSessionScopeProviderImpl(
            authenticationScopeProvider,
            rootPathsProvider,
            getGlobalScope(),
            kaliumConfigs,
            globalPreferences,
            globalCallManager,
            userStorageProvider,
            networkStateObserver,
            userAgent
        )
    }
}

