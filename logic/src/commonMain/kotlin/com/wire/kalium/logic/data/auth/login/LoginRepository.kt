package com.wire.kalium.logic.data.auth.login

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.user.login.LoginApi

interface LoginRepository {
    suspend fun loginWithEmail(
        email: String,
        password: String,
        shouldPersistClient: Boolean,
        serverConfig: ServerConfig
    ): Either<NetworkFailure, Pair<SelfUser, AuthSession>>

    suspend fun loginWithHandle(
        handle: String,
        password: String,
        shouldPersistClient: Boolean,
        serverConfig: ServerConfig
    ): Either<NetworkFailure, Pair<SelfUser, AuthSession>>
}

class LoginRepositoryImpl(
    private val loginApi: LoginApi,
    private val clientLabel: String,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
    private val userMapper: UserMapper = MapperProvider.userMapper()
) : LoginRepository {

    override suspend fun loginWithEmail(
        email: String,
        password: String,
        shouldPersistClient: Boolean,
        serverConfig: ServerConfig
    ): Either<NetworkFailure, Pair<SelfUser, AuthSession>> =
        login(LoginApi.LoginParam.LoginWithEmail(email, password, clientLabel), shouldPersistClient, serverConfig)

    override suspend fun loginWithHandle(
        handle: String,
        password: String,
        shouldPersistClient: Boolean,
        serverConfig: ServerConfig
    ): Either<NetworkFailure, Pair<SelfUser, AuthSession>> =
        login(LoginApi.LoginParam.LoginWithHandel(handle, password, clientLabel), shouldPersistClient, serverConfig)

    private suspend fun login(
        loginParam: LoginApi.LoginParam,
        persistClient: Boolean,
        serverConfig: ServerConfig
    ): Either<NetworkFailure, Pair<SelfUser, AuthSession>> =
        wrapApiRequest {
            loginApi.login(param = loginParam, persist = persistClient, apiBaseUrl = serverConfig.apiBaseUrl)
        }.map { authResult ->
            // TODO:check if the logged is user is or bot/service
            Pair(userMapper.fromDtoToSelfUser(authResult.selfUser), sessionMapper.fromSessionDTO(authResult.session, serverConfig))
        }
}
