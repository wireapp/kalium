package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.di.MapperProvider

class SSOLoginScope(
    private val ssoLoginRepository: SSOLoginRepository,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper()
) {
    private val validateSSOCodeUseCase: ValidateSSOCodeUseCase get() = ValidateSSOCodeUseCaseImpl()
    val initiate: SSOInitiateLoginUseCase get() = SSOInitiateLoginUseCaseImpl(ssoLoginRepository, validateSSOCodeUseCase)
    val finalize: SSOFinalizeLoginUseCase get() = SSOFinalizeLoginUseCaseImpl(ssoLoginRepository)
    val getLoginSessionGet: GetSSOLoginSessionUseCase get() = GetSSOLoginSessionUseCaseImpl(ssoLoginRepository, sessionMapper)
    val metaData: SSOMetaDataUseCase get() = SSOMetaDataUseCaseImpl(ssoLoginRepository)
    val settings: SSOSettingsUseCase get() = SSOSettingsUseCaseImpl(ssoLoginRepository)
}
