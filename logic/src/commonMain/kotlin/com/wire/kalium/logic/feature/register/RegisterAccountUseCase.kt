package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isBlackListedEmail
import com.wire.kalium.network.exceptions.isDomainBlockedForRegistration
import com.wire.kalium.network.exceptions.isInvalidCode
import com.wire.kalium.network.exceptions.isInvalidEmail
import com.wire.kalium.network.exceptions.isKeyExists
import com.wire.kalium.network.exceptions.isTooMAnyMembers
import com.wire.kalium.network.exceptions.isUserCreationRestricted

sealed class RegisterParam(
    firstName: String,
    lastName: String,
    val email: String,
    val password: String,
) {
    val name: String

    init {
        name = "$firstName $lastName"
    }

    class PrivateAccount(
        firstName: String,
        LastName: String,
        email: String,
        password: String,
        val emailActivationCode: String
    ) : RegisterParam(firstName, LastName, email, password)
}

class RegisterAccountUseCase(
    private val registerAccountRepository: RegisterAccountRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(param: RegisterParam, serverConfig: ServerConfig): RegisterResult {
        return when (param) {
            is RegisterParam.PrivateAccount -> {
                with(param) {
                    registerAccountRepository.registerWithEmail(email, emailActivationCode, name, password, serverConfig)
                }
            }
        }.fold(
            {
                if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                    handleSpecialErrors(it.kaliumException)
                } else {
                    RegisterResult.Failure.Generic(it)
                }
            }, {
                sessionRepository.storeSession(it.second)
                sessionRepository.updateCurrentSession(it.second.userId)
                RegisterResult.Success(it)
            })
    }

    private fun handleSpecialErrors(error: KaliumException.InvalidRequestError) =
        with(error) {
            when {
                isInvalidEmail() -> RegisterResult.Failure.InvalidEmail
                isInvalidCode() -> RegisterResult.Failure.InvalidActivationCode
                isKeyExists() -> RegisterResult.Failure.AccountAlreadyExists
                isBlackListedEmail() -> RegisterResult.Failure.BlackListed
                isUserCreationRestricted() -> RegisterResult.Failure.UserCreationRestricted
                isTooMAnyMembers() -> RegisterResult.Failure.TeamMaxMembers
                isDomainBlockedForRegistration() -> RegisterResult.Failure.EmailDomainBlocked
                else -> RegisterResult.Failure.Generic(NetworkFailure.ServerMiscommunication(this))
            }
        }


}


sealed class RegisterResult {
    class Success(val value: Pair<SelfUser, AuthSession>) : RegisterResult()
    sealed class Failure : RegisterResult() {
        object EmailDomainBlocked : Failure()
        object AccountAlreadyExists : Failure()
        object InvalidActivationCode : Failure()
        object UserCreationRestricted : Failure()
        object TeamMaxMembers : Failure()
        object BlackListed : Failure()
        object InvalidEmail : Failure()
        class Generic(val failure: NetworkFailure) : Failure()
    }
}
