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

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.sync.WorkSchedulerProvider
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider

expect class CoreLogic : CoreLogicCommon {
    override val globalPreferences: GlobalPrefProvider
    override val globalDatabaseBuilder: GlobalDatabaseBuilder
    override val userSessionScopeProvider: Lazy<UserSessionScopeProvider>
    override fun getSessionScope(userId: UserId): UserSessionScope
    override suspend fun deleteSessionScope(userId: UserId)
    override val globalCallManager: GlobalCallManager
    override val workSchedulerProvider: WorkSchedulerProvider
    override val networkStateObserver: NetworkStateObserver
    override val audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder
}

expect val clientPlatform: String
