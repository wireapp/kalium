/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.register

import com.benasher44.uuid.uuid4
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isBlackListedEmail
import com.wire.kalium.network.exceptions.isDomainBlockedForRegistration
import com.wire.kalium.network.exceptions.isInvalidCode
import com.wire.kalium.network.exceptions.isInvalidEmail
import com.wire.kalium.network.exceptions.isKeyExists
import com.wire.kalium.network.exceptions.isTooManyMembers
import com.wire.kalium.network.exceptions.isUserCreationRestricted


sealed class RegisterParam(
    open val name: String,
    val email: String,
    val password: String,
    val emailActivationCode: String,
    val cookieLabel: String?
) {

    @Deprecated("This belongs to the old flow, it is overridden by [PersonalAccount]")
    class PrivateAccount(
        val firstName: String,
        val lastName: String,
        email: String,
        password: String,
        emailActivationCode: String,
        cookieLabel: String? = uuid4().toString(),
    ) : RegisterParam("$firstName $lastName", email, password, emailActivationCode, cookieLabel) {

        override val name: String
            get() = "$firstName $lastName"
    }

    @Deprecated("This belongs to the old flow, it will be overridden by when implementing new native Team flow")
    @Suppress("LongParameterList")
    class Team(
        val firstName: String,
        val lastName: String,
        email: String,
        password: String,
        emailActivationCode: String,
        val teamName: String,
        val teamIcon: String,
        cookieLabel: String? = uuid4().toString()
    ) : RegisterParam("$firstName $lastName", email, password, emailActivationCode, cookieLabel) {

        override val name: String
            get() = "$firstName $lastName"
    }

    class PersonalAccount(
        override val name: String,
        email: String,
        password: String,
        emailActivationCode: String,
        cookieLabel: String? = uuid4().toString(),
    ) : RegisterParam(name, email, password, emailActivationCode, cookieLabel)
}

/**
 * This use case is responsible for registering a new account.
 */
class RegisterAccountUseCase internal constructor(
    private val registerAccountRepository: RegisterAccountRepository,
    private val serverConfig: ServerConfig,
    private val proxyCredentials: ProxyCredentials?
) {
    /**
     * @see [RegisterParam.PrivateAccount] and [RegisterParam.Team]
     * @param param [RegisterParam] the registration to create a private account or a team account
     *
     * @return [RegisterResult] with credentials if successful or [RegisterResult.Failure] with the specific error
     */
    suspend operator fun invoke(
        param: RegisterParam
    ): RegisterResult = when (param) {
        is RegisterParam.PersonalAccount,
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
            handleSpecialErrors(it.kaliumException as KaliumException.InvalidRequestError)
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
        val authData: AccountTokens,
        val ssoID: SsoId?,
        val serverConfigId: String,
        val proxyCredentials: ProxyCredentials?
    ) : RegisterResult()

    sealed class Failure : RegisterResult() {
        data object EmailDomainBlocked : Failure()
        data object AccountAlreadyExists : Failure()
        data object InvalidActivationCode : Failure()
        data object UserCreationRestricted : Failure()
        data object TeamMembersLimitReached : Failure()
        data object BlackListed : Failure()
        data object InvalidEmail : Failure()
        data class Generic(val failure: CoreFailure) : Failure()
    }
}
