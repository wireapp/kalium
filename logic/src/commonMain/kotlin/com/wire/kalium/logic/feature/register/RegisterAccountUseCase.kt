package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.feature.auth.AuthTokens
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isBlackListedEmail
import com.wire.kalium.network.exceptions.isDomainBlockedForRegistration
import com.wire.kalium.network.exceptions.isInvalidCode
import com.wire.kalium.network.exceptions.isInvalidEmail
import com.wire.kalium.network.exceptions.isKeyExists
import com.wire.kalium.network.exceptions.isTooManyMembers
import com.wire.kalium.network.exceptions.isUserCreationRestricted

sealed class RegisterParam(
    firstName: String,
    lastName: String,
    val email: String,
    val password: String,
) {
    val name: String = "$firstName $lastName"

    class PrivateAccount(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        val emailActivationCode: String
    ) : RegisterParam(firstName, lastName, email, password)

    @Suppress("LongParameterList")
    class Team(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        val emailActivationCode: String,
        val teamName: String,
        val teamIcon: String
    ) : RegisterParam(firstName, lastName, email, password)
}

class RegisterAccountUseCase internal constructor(
    private val registerAccountRepository: RegisterAccountRepository,
    private val serverConfig: ServerConfig
) {
    suspend operator fun invoke(
        param: RegisterParam
    ): RegisterResult = when (param) {
        is RegisterParam.PrivateAccount -> {
            with(param) {
                registerAccountRepository.registerPersonalAccountWithEmail(email, emailActivationCode, name, password)
            }
        }

        is RegisterParam.Team -> {
            with(param) {
                registerAccountRepository.registerTeamWithEmail(
                    email,
                    emailActivationCode,
                    name,
                    password,
                    teamName,
                    teamIcon
                )
            }
        }
    }.map { (ssoId, authTokens) ->
               RegisterResult.Success(authTokens, ssoId, serverConfig.id)
    }.fold({
        if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
            handleSpecialErrors(it.kaliumException)
        } else {
            RegisterResult.Failure.Generic(it)
        }
    }, {
        it
    })

    private fun handleSpecialErrors(error: KaliumException.InvalidRequestError) = with(error) {
        when {
            isInvalidEmail() -> RegisterResult.Failure.InvalidEmail
            isInvalidCode() -> RegisterResult.Failure.InvalidActivationCode
            isKeyExists() -> RegisterResult.Failure.AccountAlreadyExists
            isBlackListedEmail() -> RegisterResult.Failure.BlackListed
            isUserCreationRestricted() -> RegisterResult.Failure.UserCreationRestricted
            isTooManyMembers() -> RegisterResult.Failure.TeamMembersLimitReached
            isDomainBlockedForRegistration() -> RegisterResult.Failure.EmailDomainBlocked
            else -> RegisterResult.Failure.Generic(NetworkFailure.ServerMiscommunication(this))
        }
    }
}

sealed class RegisterResult {
    data class Success(
        val authData: AuthTokens,
        val ssoID: SsoId?,
        val serverConfigId: String
    ) : RegisterResult()

    sealed class Failure : RegisterResult() {
        object EmailDomainBlocked : Failure()
        object AccountAlreadyExists : Failure()
        object InvalidActivationCode : Failure()
        object UserCreationRestricted : Failure()
        object TeamMembersLimitReached : Failure()
        object BlackListed : Failure()
        object InvalidEmail : Failure()
        class Generic(val failure: CoreFailure) : Failure()
    }
}
