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
import com.wire.kalium.logic.data.auth.settings.PendingLoginSystemSettingsRepository
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides

/** Captures are per fresh SSO facade, including its dedicated pending-login repository. */
internal class SSOLoginGraphCapture(
    val repository: SSOLoginRepository,
    val serverConfig: ServerConfig,
    val proxyCredentials: ProxyCredentials?,
    val pendingLoginSystemSettingsRepository: PendingLoginSystemSettingsRepository,
)

internal interface SSOLoginApi {
    val initiate: SSOInitiateLoginUseCase
    val finalize: SSOFinalizeLoginUseCase
    val getLoginSession: GetSSOLoginSessionUseCase
    val fetchSSOSettings: FetchSSOSettingsUseCase
    val metaData: SSOMetaDataUseCase
    val settings: SSOSettingsUseCase
}

/** Every use case remains fresh; the supplied capture preserves aliases within one facade. */
@GraphExtension
internal interface SSOLoginGraph : SSOLoginApi {
    @Provides
    fun provideValidateSSOCode(): ValidateSSOCodeUseCase = ValidateSSOCodeUseCaseImpl()

    @Provides
    fun provideInitiate(capture: SSOLoginGraphCapture, validateSSOCode: ValidateSSOCodeUseCase): SSOInitiateLoginUseCase =
        SSOInitiateLoginUseCaseImpl(capture.repository, validateSSOCode, capture.serverConfig)

    @Provides
    fun provideFinalize(capture: SSOLoginGraphCapture): SSOFinalizeLoginUseCase = SSOFinalizeLoginUseCaseImpl(capture.repository)

    @Provides
    fun provideGetLoginSession(capture: SSOLoginGraphCapture): GetSSOLoginSessionUseCase =
        GetSSOLoginSessionUseCaseImpl(capture.repository, capture.proxyCredentials, capture.pendingLoginSystemSettingsRepository)

    @Provides
    fun provideFetchSSOSettings(capture: SSOLoginGraphCapture): FetchSSOSettingsUseCase = FetchSSOSettingsUseCase(capture.repository)

    @Provides
    fun provideMetaData(capture: SSOLoginGraphCapture): SSOMetaDataUseCase = SSOMetaDataUseCaseImpl(capture.repository)

    @Provides
    fun provideSettings(capture: SSOLoginGraphCapture): SSOSettingsUseCase = SSOSettingsUseCaseImpl(capture.repository)

    @GraphExtension.Factory
    interface Factory {
        fun create(@Provides capture: SSOLoginGraphCapture): SSOLoginGraph
    }
}
