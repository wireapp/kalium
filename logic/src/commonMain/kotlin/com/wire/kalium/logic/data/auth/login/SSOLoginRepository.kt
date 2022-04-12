package com.wire.kalium.logic.data.auth.login

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.user.login.SSOLoginApi
import com.wire.kalium.network.api.user.login.SSOSettingsResponse
import io.ktor.http.Url

interface SSOLoginRepository {

    suspend fun initiate(
        code: String,
        successRedirect: String,
        errorRedirect: String,
        serverConfig: ServerConfig
    ): Either<NetworkFailure, String>

    suspend fun initiate(code: String, serverConfig: ServerConfig): Either<NetworkFailure, String>

    suspend fun finalize(cookie: String, serverConfig: ServerConfig): Either<NetworkFailure, String>

    suspend fun metaData(serverConfig: ServerConfig): Either<NetworkFailure, String>

    suspend fun settings(serverConfig: ServerConfig): Either<NetworkFailure, SSOSettingsResponse>
}

class SSOLoginRepositoryImpl(private val ssoLoginApi: SSOLoginApi) : SSOLoginRepository {

    override suspend fun initiate(
        code: String,
        successRedirect: String,
        errorRedirect: String,
        serverConfig: ServerConfig
    ): Either<NetworkFailure, String> =
        wrapApiRequest {
            ssoLoginApi.initiate(SSOLoginApi.InitiateParam.Redirect(successRedirect, errorRedirect, code), Url(serverConfig.apiBaseUrl))
        }

    override suspend fun initiate(code: String, serverConfig: ServerConfig): Either<NetworkFailure, String> =
        wrapApiRequest {
            ssoLoginApi.initiate(SSOLoginApi.InitiateParam.NoRedirect(code), Url(serverConfig.apiBaseUrl))
        }

    override suspend fun finalize(cookie: String, serverConfig: ServerConfig): Either<NetworkFailure, String> =
        wrapApiRequest {
            ssoLoginApi.finalize(cookie, Url(serverConfig.apiBaseUrl))
        }

    override suspend fun metaData(serverConfig: ServerConfig): Either<NetworkFailure, String> =
        wrapApiRequest {
            ssoLoginApi.metaData(Url(serverConfig.apiBaseUrl))
        }

    override suspend fun settings(serverConfig: ServerConfig): Either<NetworkFailure, SSOSettingsResponse> =
        wrapApiRequest {
            ssoLoginApi.settings(Url(serverConfig.apiBaseUrl))
        }
}
