package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.feature.auth.ValidateUUIDUseCase
import com.wire.kalium.logic.feature.auth.ValidateUUIDUseCaseImpl

class SSOLoginScope(
    private val ssoLoginRepository: SSOLoginRepository
) {
    private val validateUUIDUseCase: ValidateUUIDUseCase get() = ValidateUUIDUseCaseImpl()
    val initiate: SSOInitiateLoginUseCase get() = SSOInitiateLoginUseCaseImpl(ssoLoginRepository, validateUUIDUseCase)
    val finalize: SSOFinalizeLoginUseCase get() = SSOFinalizeLoginUseCaseImpl(ssoLoginRepository)
    val metaData: SSOMetaDataUseCase get() = SSOMetaDataUseCaseImpl(ssoLoginRepository)
    val settings: SSOSettingsUseCase get() = SSOSettingsUseCaseImpl(ssoLoginRepository)
}
