package com.wire.kalium.logic.feature.register

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
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
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

sealed class RegisterParam(
    firstName: String,
    lastName: String,
    val email: String,
    val password: String,
    val cookieLabel: String?
) {
    val name: String = "$firstName $lastName"

    class PrivateAccount(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        val emailActivationCode: String,
        cookieLabel: String? = uuid4().toString(),
    ) : RegisterParam(firstName, lastName, email, password, cookieLabel)

    @Suppress("LongParameterList")
    class Team(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        val emailActivationCode: String,
        val teamName: String,
        val teamIcon: String,
        cookieLabel: String? = uuid4().toString()
    ) : RegisterParam(firstName, lastName, email, password, cookieLabel)
}

/**
 * This use case is responsible for registering a new account.
 */
class RegisterAccountUseCase internal constructor(
    private val registerAccountRepository: RegisterAccountRepository,
    private val serverConfig: ServerConfig,
    private val proxyCredentials: ProxyCredentials?,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    /**
     * @see [RegisterParam.PrivateAccount] and [RegisterParam.Team]
     * @param param [RegisterParam] the registration to create a private account or a team account
     *
     * @return [RegisterResult] with credentials if successful or [RegisterResult.Failure] with the specific error
     */
    suspend operator fun invoke(
        param: RegisterParam
    ): RegisterResult = withContext(dispatchers.default) {
        when (param) {
            is RegisterParam.PrivateAccount -> {
                with(param) {
                    registerAccountRepository.registerPersonalAccountWithEmail(
                        email = email,
                        code = emailActivationCode,
                        name = name,
                        password = password,
                        cookieLabel = cookieLabel
                    )
                }
            }

            is RegisterParam.Team -> {
                with(param) {
                    registerAccountRepository.registerTeamWithEmail(
                        email = email,
                        code = emailActivationCode,
                        name = name,
                        password = password,
                        teamName = teamName,
                        teamIcon = teamIcon,
                        cookieLabel = cookieLabel
                    )
                }
            }
        }.map { (ssoId, authTokens) ->
            RegisterResult.Success(authTokens, ssoId, serverConfig.id, proxyCredentials)
        }.fold({
            if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                handleSpecialErrors(it.kaliumException)
            } else {
                RegisterResult.Failure.Generic(it)
            }
        }, {
            it
        })
    }

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
        val serverConfigId: String,
        val proxyCredentials: ProxyCredentials?
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
