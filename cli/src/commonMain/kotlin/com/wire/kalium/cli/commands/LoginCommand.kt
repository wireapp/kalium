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

package com.wire.kalium.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.verification.VerifiableAction
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.client.RegisterClientUseCase
import com.wire.kalium.logic.feature.server.GetServerConfigResult
import com.wire.kalium.util.DelicateKaliumApi
import kotlinx.coroutines.runBlocking

class LoginCommand : CliktCommand(name = "login") {

    private val coreLogic by requireObject<CoreLogic>()
    private var userSession: UserSessionScope? = null
    private val email: String by option("-e", "--email", help = "Account email").prompt("email", promptSuffix = ": ")
    private val password: String by option("-p", "--password", help = "Account password").prompt(
        "password",
        promptSuffix = ": ",
        hideInput = true
    )
    private val environment: String? by option(
        help = "Choose backend environment: can be production, staging or an URL to a server configuration"
    )

    private suspend fun serverConfig(): ServerConfig.Links {
        return environment?.let { env ->
            when (env) {
                "staging" -> ServerConfig.STAGING
                "production" -> ServerConfig.PRODUCTION
                else -> {
                    coreLogic.globalScope {
                        when (val result = fetchServerConfigFromDeepLink(env)) {
                            is GetServerConfigResult.Success -> result.serverConfigLinks
                            is GetServerConfigResult.Failure ->
                                throw PrintMessage("failed to fetch server config from: $env")
                        }
                    }
                }
            }
        } ?: ServerConfig.DEFAULT
    }

    private suspend fun provideVersionedAuthenticationScope(serverLinks: ServerConfig.Links): AuthenticationScope =
        // CLI does not support proxy mode so we can pass null here
        when (val result = coreLogic.versionedAuthenticationScope(serverLinks).invoke(null)) {
            is AutoVersionAuthScopeUseCase.Result.Failure.Generic ->
                throw PrintMessage("failed to create authentication scope: ${result.genericFailure}")

            AutoVersionAuthScopeUseCase.Result.Failure.TooNewVersion ->
                throw PrintMessage("failed to create authentication scope: api version not supported")

            AutoVersionAuthScopeUseCase.Result.Failure.UnknownServerVersion ->
                throw PrintMessage("failed to create authentication scope: unknown server version")

            is AutoVersionAuthScopeUseCase.Result.Success -> result.authenticationScope
        }

    private suspend fun authenticate(secondFactorVerificationCode: String? = null): AuthenticationResult =
        provideVersionedAuthenticationScope(serverConfig()).let { authenticationScope ->
            authenticationScope.login(
                email,
                password,
                shouldPersistClient = false,
                secondFactorVerificationCode = secondFactorVerificationCode
            ).let {
                when (it) {
                    is AuthenticationResult.Failure.InvalidCredentials.Missing2FA -> {
                        echo("Second factor authentication required, check your e-mail for the 2fa code")
                        authenticationScope.requestSecondFactorVerificationCode(
                            email,
                            VerifiableAction.LOGIN_OR_CLIENT_REGISTRATION
                        )
                        authenticate(prompt("2fa-code"))
                    }

                    is AuthenticationResult.Failure.InvalidCredentials.Invalid2FA -> {
                        echo("Incorrect 2fa code")
                        authenticate(prompt("2fa-code"))
                    }

                    else -> it
                }
            }
        }

    @OptIn(DelicateKaliumApi::class)
    override fun run(): Unit = runBlocking {
        val loginResult = authenticate().let {
            if (it !is AuthenticationResult.Success) {
                throw PrintMessage("Login failed, check your credentials")
            } else {
                it
            }
        }

        val userId = coreLogic.globalScope {
            addAuthenticatedAccount(loginResult.serverConfigId, loginResult.ssoID, loginResult.authData, null, true)
            loginResult.authData.userId
        }

        coreLogic.sessionScope(userId) {
            when (client.getOrRegister(RegisterClientUseCase.RegisterClientParam(password, emptyList()))) {
                is RegisterClientResult.Failure -> throw PrintMessage("Client registration failed")
                is RegisterClientResult.Success -> echo("Login successful")
                is RegisterClientResult.E2EICertificateRequired -> echo("Login successful and e2ei is required")
            }
        }

        userSession = currentContext.findOrSetObject {
            coreLogic.getSessionScope(userId).also {
                it.syncExecutor.request { keepSyncAlwaysOn() }
            }
        }
    }
}
