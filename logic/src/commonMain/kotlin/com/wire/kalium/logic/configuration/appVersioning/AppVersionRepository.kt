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

package com.wire.kalium.logic.configuration.appVersioning

import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.unauthenticated.appVersioning.AppVersioningApi

internal interface AppVersionRepository {
    suspend fun isUpdateRequired(currentVersion: Int, blackListUrl: String): Boolean
}

internal class AppVersionRepositoryImpl(
    private val api: AppVersioningApi,
) : AppVersionRepository {

    override suspend fun isUpdateRequired(currentVersion: Int, blackListUrl: String): Boolean =
        wrapApiRequest { api.fetchAppVersionBlackList(blackListUrl) }
            .fold({
                kaliumLogger.e("$TAG: error while fetching VersionBlacklist: $it")
                false
            }) {
                kaliumLogger.i("$TAG: Fetched VersionBlacklist: $it ; currentVersion: $currentVersion")
                it.isAppNeedsToBeUpdated(currentVersion)
            }

    companion object {
        private const val TAG = "AppVersionRepository"
    }
}
