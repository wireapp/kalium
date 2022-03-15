package com.wire.kalium.logic.data.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.model.UserDTO
import com.wire.kalium.network.api.user.register.RegisterApi

interface RegisterAccountRepository {
    suspend fun requestEmailActivationCode(email: String, baseApiHost: String): Either<NetworkFailure, Unit>
    suspend fun verifyActivationCode(email: String, code: String, baseApiHost: String): Either<NetworkFailure, Unit>
    suspend fun registerWithEmail(
        email: String,
        code: String,
        name: String,
        password: String,
        baseApiHost: String
    ): Either<NetworkFailure, Pair<UserDTO, SessionDTO>>
}

class RegisterAccountDataSource(
    private val registerApi: RegisterApi
) : RegisterAccountRepository {
    override suspend fun requestEmailActivationCode(email: String, baseApiHost: String): Either<NetworkFailure, Unit> =
        requestActivation(RegisterApi.RequestActivationCodeParam.Email(email), baseApiHost)

    override suspend fun verifyActivationCode(email: String, code: String, baseApiHost: String): Either<NetworkFailure, Unit> =
        activateUser(RegisterApi.ActivationParam.Email(email, code), baseApiHost)

    override suspend fun registerWithEmail(
        email: String,
        code: String,
        name: String,
        password: String,
        baseApiHost: String
    ): Either<NetworkFailure, Pair<UserDTO, SessionDTO>> =
        register(RegisterApi.RegisterParam.PersonalAccount(email, code, name, password), baseApiHost)


    private suspend fun requestActivation(
        param: RegisterApi.RequestActivationCodeParam,
        baseApiHost: String
    ): Either<NetworkFailure, Unit> =
        wrapApiRequest { registerApi.requestActivationCode(param, baseApiHost) }

    private suspend fun activateUser(param: RegisterApi.ActivationParam, baseApiHost: String) =
        wrapApiRequest { registerApi.activate(param, baseApiHost) }

    private suspend fun register(param: RegisterApi.RegisterParam, baseApiHost: String) =
        wrapApiRequest { registerApi.register(param, baseApiHost) }
}
