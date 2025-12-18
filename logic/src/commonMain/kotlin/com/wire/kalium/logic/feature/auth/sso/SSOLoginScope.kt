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

package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository

internal class SSOLoginScope internal constructor(
    private val ssoLoginRepository: SSOLoginRepository,
    private val serverConfig: ServerConfig,
    private val proxyCredentials: ProxyCredentials?
) {
    private val validateSSOCodeUseCase: ValidateSSOCodeUseCase get() = ValidateSSOCodeUseCaseImpl()
    internal val initiate: SSOInitiateLoginUseCase
        get() = SSOInitiateLoginUseCaseImpl(
            ssoLoginRepository,
            validateSSOCodeUseCase,
            serverConfig,
        )
    internal val finalize: SSOFinalizeLoginUseCase get() = SSOFinalizeLoginUseCaseImpl(ssoLoginRepository)
    internal val getLoginSession: GetSSOLoginSessionUseCase get() = GetSSOLoginSessionUseCaseImpl(ssoLoginRepository, proxyCredentials)
    internal val fetchSSOSettings: FetchSSOSettingsUseCase get() = FetchSSOSettingsUseCase(ssoLoginRepository)
    internal val metaData: SSOMetaDataUseCase get() = SSOMetaDataUseCaseImpl(ssoLoginRepository)
    internal val settings: SSOSettingsUseCase get() = SSOSettingsUseCaseImpl(ssoLoginRepository)
}
