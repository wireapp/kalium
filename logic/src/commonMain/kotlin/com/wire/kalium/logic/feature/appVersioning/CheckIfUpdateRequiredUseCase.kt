package com.wire.kalium.logic.feature.appVersioning

import com.wire.kalium.logic.configuration.appVersioning.AppVersionRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Returns false if app needs to be updated and user should not be able app without it
 * true - otherwise
 */
interface CheckIfUpdateRequiredUseCase {
    suspend operator fun invoke(currentAppVersion: Int, blackListUrl: String): Boolean
}

internal class CheckIfUpdateRequiredUseCaseImpl(
    private val appVersionRepository: AppVersionRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : CheckIfUpdateRequiredUseCase {

    override suspend fun invoke(currentAppVersion: Int, blackListUrl: String): Boolean = withContext(dispatcher.default) {
        appVersionRepository.isUpdateRequired(currentAppVersion, blackListUrl)
    }
}
