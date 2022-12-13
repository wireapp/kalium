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
                true
            }) {
                kaliumLogger.i("$TAG: Fetched VersionBlacklist: $it ; currentVersion: $currentVersion")
                it.isAppNeedsToBeUpdated(currentVersion)
            }

    companion object {
        private const val TAG = "AppVersionRepository"
    }
}
