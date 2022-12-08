package com.wire.kalium.logic.feature.appVersioning

import com.wire.kalium.logic.configuration.appVersioning.AppVersionRepository

/**
 * Returns false if app needs to be updated and user should not be able app without it
 * true - otherwise
 */
interface CheckIfUpdateRequiredUseCase {
    suspend operator fun invoke(currentAppVersion: Int, blackListUrl: String): Boolean
}

internal class CheckIfUpdateRequiredUseCaseImpl(private val appVersionRepository: AppVersionRepository) : CheckIfUpdateRequiredUseCase {

    override suspend fun invoke(currentAppVersion: Int, blackListUrl: String): Boolean =
        appVersionRepository.isUpdateRequired(currentAppVersion, blackListUrl)
}
