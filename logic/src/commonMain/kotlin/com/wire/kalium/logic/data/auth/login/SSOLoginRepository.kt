package com.wire.kalium.logic.data.auth.login

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.model.AuthenticationResultDTO
import com.wire.kalium.network.api.base.unAuthenticated.SSOSettingsResponse
import com.wire.kalium.network.api.v0.unauthenticated.SSOLogin

interface SSOLoginRepository {

    suspend fun initiate(
        uuid: String,
        successRedirect: String,
        errorRedirect: String
    ): Either<NetworkFailure, String>

    suspend fun initiate(uuid: String): Either<NetworkFailure, String>

    suspend fun finalize(cookie: String): Either<NetworkFailure, String>

    suspend fun provideLoginSession(cookie: String): Either<NetworkFailure, AuthenticationResultDTO>

    suspend fun metaData(): Either<NetworkFailure, String>

    suspend fun settings(): Either<NetworkFailure, SSOSettingsResponse>
}

class SSOLoginRepositoryImpl(
    private val ssoLogin: SSOLogin
) : SSOLoginRepository {

    override suspend fun initiate(
        uuid: String,
        successRedirect: String,
        errorRedirect: String
    ): Either<NetworkFailure, String> = wrapApiRequest {
        ssoLogin.initiate(SSOLogin.InitiateParam.WithRedirect(successRedirect, errorRedirect, uuid))
    }

    override suspend fun initiate(uuid: String): Either<NetworkFailure, String> = wrapApiRequest {
        ssoLogin.initiate(SSOLogin.InitiateParam.WithoutRedirect(uuid))
    }

    override suspend fun finalize(cookie: String): Either<NetworkFailure, String> = wrapApiRequest {
        ssoLogin.finalize(cookie)
    }

    override suspend fun provideLoginSession(cookie: String): Either<NetworkFailure, AuthenticationResultDTO> = wrapApiRequest {
        ssoLogin.provideLoginSession(cookie)
    }

    override suspend fun metaData(): Either<NetworkFailure, String> = wrapApiRequest {
        ssoLogin.metaData()
    }

    override suspend fun settings(): Either<NetworkFailure, SSOSettingsResponse> = wrapApiRequest {
        ssoLogin.settings()
    }
}
