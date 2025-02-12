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
@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.common.functional.fold
import com.wire.kalium.network.api.unauthenticated.sso.SSOSettingsResponse

sealed class SSOSettingsResult {
    data class Success(val ssoSettings: SSOSettingsResponse) : SSOSettingsResult()

    sealed class Failure : SSOSettingsResult() {
        data class Generic(val genericFailure: CoreFailure) : Failure()
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
    private val ssoLoginRepository: SSOLoginRepository
) : SSOSettingsUseCase {

    override suspend fun invoke(): SSOSettingsResult =
        ssoLoginRepository.settings().fold({ SSOSettingsResult.Failure.Generic(it) }, { SSOSettingsResult.Success(it) })
}
