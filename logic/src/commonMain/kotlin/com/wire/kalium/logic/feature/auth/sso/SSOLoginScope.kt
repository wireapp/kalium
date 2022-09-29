package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository

class SSOLoginScope internal constructor(
    private val ssoLoginRepository: SSOLoginRepository,
    private val serverConfig: ServerConfig
) {
    private val validateSSOCodeUseCase: ValidateSSOCodeUseCase get() = ValidateSSOCodeUseCaseImpl()
    val initiate: SSOInitiateLoginUseCase
        get() = SSOInitiateLoginUseCaseImpl(
            ssoLoginRepository,
            validateSSOCodeUseCase,
            serverConfig,
        )
    val finalize: SSOFinalizeLoginUseCase get() = SSOFinalizeLoginUseCaseImpl(ssoLoginRepository)
    val getLoginSession: GetSSOLoginSessionUseCase get() = GetSSOLoginSessionUseCaseImpl(ssoLoginRepository)
    val metaData: SSOMetaDataUseCase get() = SSOMetaDataUseCaseImpl(ssoLoginRepository)
    val settings: SSOSettingsUseCase get() = SSOSettingsUseCaseImpl(ssoLoginRepository)
}
