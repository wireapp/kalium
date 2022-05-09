package com.wire.kalium.logic.data.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.user.register.RegisterApi

interface RegisterAccountRepository {
    suspend fun requestEmailActivationCode(email: String, baseApiHost: String): Either<NetworkFailure, Unit>
    suspend fun verifyActivationCode(email: String, code: String, baseApiHost: String): Either<NetworkFailure, Unit>
    suspend fun registerPersonalAccountWithEmail(
        email: String, code: String, name: String, password: String, serverConfig: ServerConfig
    ): Either<NetworkFailure, Pair<SelfUser, AuthSession>>
    suspend fun registerTeamWithEmail(
        email: String, code: String, name: String, password: String, teamName: String, teamIcon: String, serverConfig: ServerConfig
    ): Either<NetworkFailure, Pair<SelfUser, AuthSession>>
}

class RegisterAccountDataSource(
    private val registerApi: RegisterApi,
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper()
) : RegisterAccountRepository {
    override suspend fun requestEmailActivationCode(email: String, baseApiHost: String): Either<NetworkFailure, Unit> =
        requestActivation(RegisterApi.RequestActivationCodeParam.Email(email), baseApiHost)

    override suspend fun verifyActivationCode(email: String, code: String, baseApiHost: String): Either<NetworkFailure, Unit> =
        activateUser(RegisterApi.ActivationParam.Email(email, code), baseApiHost)

    override suspend fun registerPersonalAccountWithEmail(
        email: String, code: String, name: String, password: String, serverConfig: ServerConfig
    ): Either<NetworkFailure, Pair<SelfUser, AuthSession>> =
        register(RegisterApi.RegisterParam.PersonalAccount(email, code, name, password), serverConfig)

    override suspend fun registerTeamWithEmail(
        email: String, code: String, name: String, password: String, teamName: String, teamIcon: String, serverConfig: ServerConfig
    ): Either<NetworkFailure, Pair<SelfUser, AuthSession>> =
        register(RegisterApi.RegisterParam.TeamAccount(email, code, name, password, teamName, teamIcon), serverConfig)

    private suspend fun requestActivation(
        param: RegisterApi.RequestActivationCodeParam, baseApiUrl: String
    ): Either<NetworkFailure, Unit> = wrapApiRequest { registerApi.requestActivationCode(param, baseApiUrl) }

    private suspend fun activateUser(param: RegisterApi.ActivationParam, baseApiUrl: String): Either<NetworkFailure, Unit> =
        wrapApiRequest { registerApi.activate(param, baseApiUrl) }

    private suspend fun register(param: RegisterApi.RegisterParam, serverConfig: ServerConfig) =
        wrapApiRequest { registerApi.register(param, serverConfig.apiBaseUrl) }.map {
            Pair(
                userMapper.fromDtoToSelfUser(it.first), sessionMapper.fromSessionDTO(it.second, serverConfig)
            )
        }
}
