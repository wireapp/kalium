package com.wire.kalium.logic.data.auth.login

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.user.login.SSOLoginApi
import com.wire.kalium.network.api.user.login.SSOSettingsResponse

interface SSOLoginRepository {

    suspend fun initiate(
        uuid: String, successRedirect: String, errorRedirect: String
    ): Either<NetworkFailure, String>

    suspend fun initiate(uuid: String): Either<NetworkFailure, String>

    suspend fun finalize(cookie: String): Either<NetworkFailure, String>

    suspend fun provideLoginSession(cookie: String): Either<NetworkFailure, SessionDTO>

    suspend fun metaData(): Either<NetworkFailure, String>

    suspend fun settings(): Either<NetworkFailure, SSOSettingsResponse>
}

class SSOLoginRepositoryImpl(private val ssoLoginApi: SSOLoginApi) : SSOLoginRepository {

    override suspend fun initiate(
        uuid: String, successRedirect: String, errorRedirect: String
    ): Either<NetworkFailure, String> = wrapApiRequest {
        ssoLoginApi.initiate(SSOLoginApi.InitiateParam.WithRedirect(successRedirect, errorRedirect, uuid))
    }

    override suspend fun initiate(uuid: String): Either<NetworkFailure, String> = wrapApiRequest {
        ssoLoginApi.initiate(SSOLoginApi.InitiateParam.WithoutRedirect(uuid))
    }

    override suspend fun finalize(cookie: String): Either<NetworkFailure, String> = wrapApiRequest {
        ssoLoginApi.finalize(cookie)
    }

    override suspend fun provideLoginSession(cookie: String): Either<NetworkFailure, SessionDTO> = wrapApiRequest {
        ssoLoginApi.provideLoginSession(cookie)
    }


    override suspend fun metaData(): Either<NetworkFailure, String> = wrapApiRequest {
        ssoLoginApi.metaData()
    }

    override suspend fun settings(): Either<NetworkFailure, SSOSettingsResponse> = wrapApiRequest {
        ssoLoginApi.settings()
    }
}
