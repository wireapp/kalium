package com.wire.kalium.logic.data.auth.login

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.user.login.SSOLoginApi
import com.wire.kalium.network.api.user.login.SSOSettingsResponse
import io.ktor.http.Url

interface SSOLoginRepository {

    suspend fun initiate(
        uuid: String,
        successRedirect: String,
        errorRedirect: String,
        serverConfig: ServerConfig
    ): Either<NetworkFailure, String>

    suspend fun initiate(uuid: String, serverConfig: ServerConfig): Either<NetworkFailure, String>

    suspend fun finalize(cookie: String, serverConfig: ServerConfig): Either<NetworkFailure, String>

    suspend fun provideLoginSession(cookie: String, serverConfig: ServerConfig): Either<NetworkFailure, SessionDTO>

    suspend fun metaData(serverConfig: ServerConfig): Either<NetworkFailure, String>

    suspend fun settings(serverConfig: ServerConfig): Either<NetworkFailure, SSOSettingsResponse>
}

class SSOLoginRepositoryImpl(private val ssoLoginApi: SSOLoginApi) : SSOLoginRepository {

    override suspend fun initiate(
        uuid: String,
        successRedirect: String,
        errorRedirect: String,
        serverConfig: ServerConfig
    ): Either<NetworkFailure, String> =
        wrapApiRequest {
            ssoLoginApi.initiate(SSOLoginApi.InitiateParam.WithRedirect(successRedirect, errorRedirect, uuid), Url(serverConfig.apiBaseUrl))
        }

    override suspend fun initiate(uuid: String, serverConfig: ServerConfig): Either<NetworkFailure, String> =
        wrapApiRequest {
            ssoLoginApi.initiate(SSOLoginApi.InitiateParam.WithoutRedirect(uuid), Url(serverConfig.apiBaseUrl))
        }

    override suspend fun finalize(cookie: String, serverConfig: ServerConfig): Either<NetworkFailure, String> =
        wrapApiRequest {
            ssoLoginApi.finalize(cookie, Url(serverConfig.apiBaseUrl))
        }

    override suspend fun provideLoginSession(cookie: String, serverConfig: ServerConfig): Either<NetworkFailure, SessionDTO> =
        wrapApiRequest {
            ssoLoginApi.provideLoginSession(cookie, Url(serverConfig.apiBaseUrl))
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
