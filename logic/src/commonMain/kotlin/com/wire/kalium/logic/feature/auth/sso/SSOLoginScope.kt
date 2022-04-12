package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.data.auth.login.SSOLoginRepository

class SSOLoginScope(
    private val ssoLoginRepository: SSOLoginRepository
) {
    private val validateSSOCodeUseCase: ValidateSSOCodeUseCase get() = ValidateSSOCodeUseCaseImpl()
    val initiate: SSOInitiateLoginUseCase get() = SSOInitiateLoginUseCaseImpl(ssoLoginRepository, validateSSOCodeUseCase)
    val finalize: SSOFinalizeLoginUseCase get() = SSOFinalizeLoginUseCaseImpl(ssoLoginRepository)
    val metaData: SSOMetaDataUseCase get() = SSOMetaDataUseCaseImpl(ssoLoginRepository)
    val settings: SSOSettingsUseCase get() = SSOSettingsUseCaseImpl(ssoLoginRepository)
}
