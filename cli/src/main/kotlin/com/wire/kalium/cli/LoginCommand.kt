package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.client.RegisterClientUseCase
import com.wire.kalium.logic.feature.server.GetServerConfigResult
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
        when (val result = coreLogic.versionedAuthenticationScope(serverLinks).invoke()) {
            is AutoVersionAuthScopeUseCase.Result.Failure.Generic ->
                throw PrintMessage("failed to create authentication scope: ${result.genericFailure}")

            AutoVersionAuthScopeUseCase.Result.Failure.TooNewVersion ->
                throw PrintMessage("failed to create authentication scope: api version not supported")

            AutoVersionAuthScopeUseCase.Result.Failure.UnknownServerVersion ->
                throw PrintMessage("failed to create authentication scope: unknown server version")

            is AutoVersionAuthScopeUseCase.Result.Success -> result.authenticationScope
        }

    override fun run(): Unit = runBlocking {
        val loginResult = provideVersionedAuthenticationScope(serverConfig()).login(email, password, true).let {
            if (it !is AuthenticationResult.Success) {
                throw PrintMessage("Login failed, check your credentials")
            } else {
                it
            }
        }

        val userId = coreLogic.globalScope {
            addAuthenticatedAccount(loginResult.serverConfigId, loginResult.ssoID, loginResult.authData, true)
            loginResult.authData.userId
        }

        coreLogic.sessionScope(userId) {
            if (client.needsToRegisterClient()) {
                when (client.register(RegisterClientUseCase.RegisterClientParam(password, emptyList()))) {
                    is RegisterClientResult.Failure -> throw PrintMessage("Client registration failed")
                    is RegisterClientResult.Success -> echo("Login successful")
                }
            }
        }

        userSession = currentContext.findOrSetObject { coreLogic.getSessionScope(userId) }
    }
}
