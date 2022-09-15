package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.AuthenticatedDataSourceSet

interface ClearUserDataUseCase {
    suspend operator fun invoke()
}

internal class ClearUserDataUseCaseImpl internal constructor(
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet
) : ClearUserDataUseCase {

    override suspend operator fun invoke() {
        clearUserStorage()
    }

    private fun clearUserStorage() {
        authenticatedDataSourceSet.userDatabaseProvider.nuke()
        // exclude clientId clear from this step
        authenticatedDataSourceSet.kaliumPreferencesSettings.nuke()
    }
}
