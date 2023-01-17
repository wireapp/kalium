package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.api.base.unauthenticated.SSOSettingsResponse
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

sealed class SSOSettingsResult {
    data class Success(val ssoSettings: SSOSettingsResponse) : SSOSettingsResult()

    sealed class Failure : SSOSettingsResult() {
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

/**
 * Gets the SSO settings
 */
interface SSOSettingsUseCase {
    /**
     * @return the [SSOSettingsResult] with the default_sso_code settings if successful
     */
    suspend operator fun invoke(): SSOSettingsResult
}

internal class SSOSettingsUseCaseImpl(
    private val ssoLoginRepository: SSOLoginRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : SSOSettingsUseCase {

    override suspend fun invoke(): SSOSettingsResult = withContext(dispatcher.default) {
        ssoLoginRepository.settings().fold({ SSOSettingsResult.Failure.Generic(it) }, { SSOSettingsResult.Success(it) })
    }
}
