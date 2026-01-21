/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic

import android.content.Context
import com.waz.audioeffect.AudioEffect
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.UserSessionScopeProviderImpl
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilderImpl
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.network.NetworkStateObserverImpl
import com.wire.kalium.logic.sync.WorkSchedulerProvider
import com.wire.kalium.logic.sync.WorkSchedulerProviderImpl
import com.wire.kalium.logic.util.PlatformContext
import com.wire.kalium.logic.util.SecurityHelperImpl
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.cancel

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from CoreLogicCommon
 */
public actual class CoreLogic(
    userAgent: String,
    private val appContext: Context,
    rootPath: String,
    kaliumConfigs: KaliumConfigs
) : CoreLogicCommon(rootPath, userAgent, kaliumConfigs) {

    actual override val globalPreferences: GlobalPrefProvider = GlobalPrefProvider(
        appContext,
        kaliumConfigs.shouldEncryptData
    )

    actual override val globalDatabaseBuilder: GlobalDatabaseBuilder = globalDatabaseProvider(
        platformDatabaseData = PlatformDatabaseData(appContext),
        queriesContext = KaliumDispatcherImpl.io,
        passphrase = if (kaliumConfigs.shouldEncryptData) {
            SecurityHelperImpl(globalPreferences.passphraseStorage).globalDBSecret()
        } else {
            null
        },
        enableWAL = true
    )

    public actual override fun getSessionScope(userId: UserId): UserSessionScope =
        userSessionScopeProvider.value.getOrCreate(userId)

    actual override suspend fun deleteSessionScope(userId: UserId) {
        userSessionScopeProvider.value.get(userId)?.cancel()
        userSessionScopeProvider.value.delete(userId)
    }

    internal actual override val globalCallManager: GlobalCallManager by lazy {
        GlobalCallManager(
            appContext = PlatformContext(appContext),
            scope = getGlobalScope(),
            networkStateObserver = networkStateObserver
        )
    }

    actual override val workSchedulerProvider: WorkSchedulerProvider = WorkSchedulerProviderImpl(appContext)

    public actual override val networkStateObserver: NetworkStateObserver = NetworkStateObserverImpl(
        appContext = appContext
    )

    actual override val userSessionScopeProvider: Lazy<UserSessionScopeProvider> = lazy {
        UserSessionScopeProviderImpl(
            authenticationScopeProvider,
            rootPathsProvider,
            appContext,
            getGlobalScope(),
            globalDatabaseBuilder,
            kaliumConfigs,
            globalPreferences,
            globalCallManager,
            userStorageProvider,
            networkStateObserver,
            logoutCallbackManager,
            userAgent
        )
    }

    public actual override val audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder = AudioNormalizedLoudnessBuilderImpl(
        dispatcher = KaliumDispatcherImpl.io,
        audioEffect = AudioEffect(appContext),
    )
}

@Suppress("MayBeConst")
internal actual val clientPlatform: String = "android"
