package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.sso.SSOUtil
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode

sealed class SSOInitiateLoginResult {
    data class Success(val requestUrl: String) : SSOInitiateLoginResult()

    sealed class Failure : SSOInitiateLoginResult() {
        object InvalidCodeFormat : Failure()
        object InvalidCode : Failure()
        object InvalidRedirect : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

data class SSORedirects(val success: String, val error: String) {
    constructor(serverConfigId: String) : this(SSOUtil.generateSuccessRedirect(serverConfigId), SSOUtil.generateErrorRedirect())
}

interface SSOInitiateLoginUseCase {
    sealed class Param {
        abstract val ssoCode: String

        data class WithoutRedirect(override val ssoCode: String) : Param()
        data class WithRedirect(
            override val ssoCode: String, val redirects: SSORedirects
        ) : Param() {
            constructor(ssoCode: String, wireServerID: String) : this(ssoCode, SSORedirects(wireServerID))
        }
    }

    suspend operator fun invoke(param: Param): SSOInitiateLoginResult
}

internal class SSOInitiateLoginUseCaseImpl(
    private val ssoLoginRepository: SSOLoginRepository, private val validateSSOCodeUseCase: ValidateSSOCodeUseCase
) : SSOInitiateLoginUseCase {

    override suspend fun invoke(param: SSOInitiateLoginUseCase.Param): SSOInitiateLoginResult = with(param) {
        val validUuid = validateSSOCodeUseCase(ssoCode).let {
            when (it) {
                is ValidateSSOCodeResult.Valid -> it.uuid
                ValidateSSOCodeResult.Invalid -> return@with SSOInitiateLoginResult.Failure.InvalidCodeFormat
            }
        }
        when (this) {
            is SSOInitiateLoginUseCase.Param.WithoutRedirect -> ssoLoginRepository.initiate(validUuid)
            is SSOInitiateLoginUseCase.Param.WithRedirect -> ssoLoginRepository.initiate(
                validUuid, redirects.success, redirects.error
            )
        }.fold({
            if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                if (it.kaliumException.errorResponse.code == HttpStatusCode.BadRequest.value)
                    return@fold SSOInitiateLoginResult.Failure.InvalidRedirect
                if (it.kaliumException.errorResponse.code == HttpStatusCode.NotFound.value)
                    return@fold SSOInitiateLoginResult.Failure.InvalidCode
            }
            SSOInitiateLoginResult.Failure.Generic(it)
        }, {
            SSOInitiateLoginResult.Success(it)
        })
    }
}
