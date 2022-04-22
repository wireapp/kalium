package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.session.SessionMapper

class SSOLoginScope(
    private val ssoLoginRepository: SSOLoginRepository,
    private val sessionMapper: SessionMapper
) {
    private val validateSSOCodeUseCase: ValidateSSOCodeUseCase get() = ValidateSSOCodeUseCaseImpl()
    val initiate: SSOInitiateLoginUseCase get() = SSOInitiateLoginUseCaseImpl(ssoLoginRepository, validateSSOCodeUseCase)
    val finalize: SSOFinalizeLoginUseCase get() = SSOFinalizeLoginUseCaseImpl(ssoLoginRepository)
    val ssoEstablishSession: SSOEstablishSessionUseCase get() = SSOEstablishSessionUseCaseImpl(ssoLoginRepository,sessionMapper)
    val metaData: SSOMetaDataUseCase get() = SSOMetaDataUseCaseImpl(ssoLoginRepository)
    val settings: SSOSettingsUseCase get() = SSOSettingsUseCaseImpl(ssoLoginRepository)
}
