/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
