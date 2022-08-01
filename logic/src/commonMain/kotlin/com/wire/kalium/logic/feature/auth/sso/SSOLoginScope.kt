package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository

class SSOLoginScope internal constructor(
    private val ssoLoginRepository: SSOLoginRepository,
    private val serverLinks: ServerConfig.Links,
    private val serverConfigRepository: ServerConfigRepository
) {
    private val validateSSOCodeUseCase: ValidateSSOCodeUseCase get() = ValidateSSOCodeUseCaseImpl()
    val initiate: SSOInitiateLoginUseCase
        get() = SSOInitiateLoginUseCaseImpl(
            ssoLoginRepository,
            validateSSOCodeUseCase,
            serverLinks,
            serverConfigRepository
        )
    val finalize: SSOFinalizeLoginUseCase get() = SSOFinalizeLoginUseCaseImpl(ssoLoginRepository)
    val getLoginSessionGet: GetSSOLoginSessionUseCase get() = GetSSOLoginSessionUseCaseImpl(ssoLoginRepository, serverLinks)
    val metaData: SSOMetaDataUseCase get() = SSOMetaDataUseCaseImpl(ssoLoginRepository)
    val settings: SSOSettingsUseCase get() = SSOSettingsUseCaseImpl(ssoLoginRepository)
}
