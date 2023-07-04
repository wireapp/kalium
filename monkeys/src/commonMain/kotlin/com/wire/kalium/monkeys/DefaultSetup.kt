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
package com.wire.kalium.monkeys

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.client.RegisterClientUseCase
import com.wire.kalium.logic.feature.client.SelfClientsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

class DefaultSetup(setupParallelism: Int) : SetupStep {

    private val dispatcher = Dispatchers.Default.limitedParallelism(setupParallelism)

    override suspend fun invoke(
        coreLogic: CoreLogic,
        accountGroups: List<List<UserData>>
    ): List<List<Monkey>> = coroutineScope {
        println("### LOGGING IN ALL USERS")
        val monkeyGroups = accountGroups.map { group ->
            group.map { accountData ->
                async(dispatcher) {
                    println("### Getting versioned backend")
                    val authScope = getAuthScope(coreLogic, accountData.backend)
                    acquireMonkeySessions(accountData, authScope, coreLogic)
                }
            }.awaitAll()
        }

        registerAllClients(monkeyGroups)

        // TODO: Connection requests between multiple federated backends

        monkeyGroups
    }

    private suspend fun registerAllClients(monkeyGroups: List<List<Monkey>>) = coroutineScope {
        for (monkey in monkeyGroups.flatten()) {
            val userData = monkey.user
            val scope = monkey.operationScope
            launch(dispatcher) {
                val registerClientParam = RegisterClientUseCase.RegisterClientParam(
                    password = userData.password,
                    capabilities = emptyList(),
                    clientType = ClientType.Temporary
                )
                val registerResult = scope.client.getOrRegister(registerClientParam)
                if (registerResult !is RegisterClientResult.Success) {
                    if (registerResult is RegisterClientResult.Failure.TooManyClients) {
                        val selfClientsResult = scope.client.selfClients()
                        if (selfClientsResult !is SelfClientsResult.Success) {
                            error("Failed to fetch other clients for user. $registerResult")
                        }
                        val oldestClient = selfClientsResult.clients.minBy {
                            it.registrationTime ?: Instant.DISTANT_PAST
                        }
                        scope.client.deleteClient(DeleteClientParam(userData.password, oldestClient.id))
                    } else {
                        error("Failed to register client for user. $registerResult")
                    }
                }
            }
        }
    }

    private suspend fun getAuthScope(coreLogic: CoreLogic, backend: Backend): AuthenticationScope {
        val result = coreLogic.versionedAuthenticationScope(
            ServerConfig.Links(
                api = backend.api,
                accounts = backend.accounts,
                webSocket = backend.webSocket,
                blackList = backend.blackList,
                teams = backend.teams,
                website = backend.website,
                title = backend.title,
                isOnPremises = true,
                apiProxy = null
            )
        ).invoke()
        if (result !is AutoVersionAuthScopeUseCase.Result.Success) {
            error("Invalid backend for whatever reason")
        }
        return result.authenticationScope
    }

    private suspend fun acquireMonkeySessions(
        accountData: UserData,
        authScope: AuthenticationScope,
        coreLogic: CoreLogic
    ): Monkey {
        val email = accountData.email
        val password = accountData.password
        val loginResult = authScope.login(email, password, false)
        if (loginResult !is AuthenticationResult.Success) {
            error("User creds didn't work ($email, $password)")
        }

        coreLogic.globalScope {
            val storeResult = addAuthenticatedAccount(
                serverConfigId = loginResult.serverConfigId,
                ssoId = loginResult.ssoID,
                authTokens = loginResult.authData,
                proxyCredentials = loginResult.proxyCredentials,
                replace = true
            )
            if (storeResult !is AddAuthenticatedUserUseCase.Result.Success) {
                error("Failed to store user. $storeResult")
            }
        }

        return Monkey(accountData, coreLogic.getSessionScope(loginResult.authData.userId))
    }
}
