package com.wire.kalium.logic.data.register

import com.wire.kalium.logic.NetworkFailure
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
    suspend fun requestEmailActivationCode(email: String): Either<NetworkFailure, Unit>
    suspend fun verifyActivationCode(email: String, code: String): Either<NetworkFailure, Unit>
    suspend fun registerPersonalAccountWithEmail(
        email: String, code: String, name: String, password: String
    ): Either<NetworkFailure, Pair<SelfUser, AuthSession.Tokens>>

    suspend fun registerTeamWithEmail(
        email: String, code: String, name: String, password: String, teamName: String, teamIcon: String
    ): Either<NetworkFailure, Pair<SelfUser, AuthSession.Tokens>>
}

class RegisterAccountDataSource(
    private val registerApi: RegisterApi,
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper()
) : RegisterAccountRepository {
    override suspend fun requestEmailActivationCode(email: String): Either<NetworkFailure, Unit> =
        requestActivation(RegisterApi.RequestActivationCodeParam.Email(email))

    override suspend fun verifyActivationCode(email: String, code: String): Either<NetworkFailure, Unit> =
        activateUser(RegisterApi.ActivationParam.Email(email, code))

    override suspend fun registerPersonalAccountWithEmail(
        email: String, code: String, name: String, password: String
    ): Either<NetworkFailure, Pair<SelfUser, AuthSession.Tokens>> =
        register(RegisterApi.RegisterParam.PersonalAccount(email, code, name, password))

    override suspend fun registerTeamWithEmail(
        email: String, code: String, name: String, password: String, teamName: String, teamIcon: String
    ): Either<NetworkFailure, Pair<SelfUser, AuthSession.Tokens>> =
        register(RegisterApi.RegisterParam.TeamAccount(email, code, name, password, teamName, teamIcon))

    private suspend fun requestActivation(
        param: RegisterApi.RequestActivationCodeParam
    ): Either<NetworkFailure, Unit> = wrapApiRequest { registerApi.requestActivationCode(param) }

    private suspend fun activateUser(param: RegisterApi.ActivationParam): Either<NetworkFailure, Unit> =
        wrapApiRequest { registerApi.activate(param) }

    private suspend fun register(param: RegisterApi.RegisterParam): Either<NetworkFailure, Pair<SelfUser, AuthSession.Tokens>> =
        wrapApiRequest { registerApi.register(param) }.map {
            Pair(userMapper.fromDtoToSelfUser(it.first), sessionMapper.fromSessionDTO(it.second))
        }
}
