package com.wire.kalium.logic.feature.auth.sso

import com.benasher44.uuid.uuidFrom
import com.wire.kalium.logic.feature.auth.sso.ValidateSSOCodeUseCase.Companion.SSO_CODE_WIRE_PREFIX

interface ValidateSSOCodeUseCase {
    operator fun invoke(ssoCode: String): ValidateSSOCodeResult

    companion object {
        const val SSO_CODE_WIRE_PREFIX = "wire-"
    }
}

class ValidateSSOCodeUseCaseImpl() : ValidateSSOCodeUseCase {
    override fun invoke(ssoCode: String): ValidateSSOCodeResult =
        if(!ssoCode.startsWith(SSO_CODE_WIRE_PREFIX)) ValidateSSOCodeResult.Invalid
        else ssoCode.removePrefix(SSO_CODE_WIRE_PREFIX).let { uuid ->
            try { uuidFrom(uuid).let { ValidateSSOCodeResult.Valid(uuid) } }
            catch (e: IllegalArgumentException) { ValidateSSOCodeResult.Invalid }
        }
}

sealed class ValidateSSOCodeResult {
    data class Valid(val uuid: String): ValidateSSOCodeResult()
    object Invalid: ValidateSSOCodeResult()
}
