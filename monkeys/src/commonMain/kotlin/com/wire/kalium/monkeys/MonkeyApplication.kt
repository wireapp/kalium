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

@file:Suppress("TooManyFunctions")

package com.wire.kalium.monkeys

import co.touchlab.kermit.LogWriter
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.LoginUseCase
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.feature.client.ClientScope
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.client.RegisterClientUseCase
import com.wire.kalium.logic.feature.conversation.GetConversationUseCase
import com.wire.kalium.logic.feature.conversation.GetConversationsUseCase
import com.wire.kalium.logic.feature.register.RegisterResult
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

class MonkeyApplication : CliktCommand(allowMultipleSubcommands = true) {

    private val logLevel by option(help = "log level").enum<KaliumLogLevel>().default(KaliumLogLevel.DEBUG)
    private val logOutputFile by option(help = "output file for logs")
    private val fileLogger: LogWriter by lazy { fileLogger(logOutputFile ?: "kalium.log") }

    override fun run() = runBlocking {
        val coreLogic = coreLogic(
            rootPath = "$HOME_DIRECTORY/.kalium/accounts",
            kaliumConfigs = KaliumConfigs(
                developmentApiEnabled = true,
                encryptProteusStorage = true,
                isMLSSupportEnabled = true,
            )
        )

        if (logOutputFile != null) {
            CoreLogger.setLoggingLevel(logLevel, fileLogger)
        } else {
            CoreLogger.setLoggingLevel(logLevel)
        }

        coreLogic.updateApiVersionsScheduler.scheduleImmediateApiVersionUpdate()
        runMonkey(coreLogic)
    }

    private suspend fun runMonkey(coreLogic: CoreLogic) {
        val result = coreLogic.versionedAuthenticationScope(ANTA_SERVER_CONFIGS).invoke()
        if (result !is AutoVersionAuthScopeUseCase.Result.Success) {
            error("Invalid backend for whatever reason")
        }

        val allUsers = hardCodedUsers.map { accountData ->
            val email = accountData.email
            val password = accountData.password
            val loginResult = result.authenticationScope.login(email, password, false)
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

            accountData to coreLogic.getSessionScope(loginResult.authData.userId)
        }.toMap()

        registerAllClients(allUsers)

        while (true) {
            val randomUser = allUsers.entries.random()
            val userScope = randomUser.value
            val conversationResult = randomUser.value.conversations.getConversations()
            if (conversationResult !is GetConversationsUseCase.Result.Success) {
                error("Failure to get conversations for ${randomUser.key}; $conversationResult")
            }
            val firstConversation = conversationResult.convFlow.first().firstOrNull()

            if (firstConversation == null) {
                echo("User has no conversation")
                continue
            }
            userScope.messages.sendTextMessage(
                firstConversation.id,
                "give banana!",
            )
            delay(1.seconds)
        }
    }

    private suspend fun registerAllClients(
        allUsers: Map<UserData, UserSessionScope>
    ) = coroutineScope {
        for (entry in allUsers.entries) {
            val (userData, scope) = entry
            launch {
                val registerClientParam = RegisterClientUseCase.RegisterClientParam(
                    password = userData.password,
                    capabilities = emptyList(),
                    clientType = ClientType.Temporary
                )
                val registerResult = scope.client.getOrRegister(registerClientParam)
                if (registerResult !is RegisterClientResult.Success) {
                    error("Failed to register client for user. $registerResult")
                }
            }
        }
    }

    companion object {
        val HOME_DIRECTORY: String = homeDirectory()
        val ANTA_SERVER_CONFIGS = ServerConfig.Links(
            api = "https://nginz-https.anta.wire.link",
            accounts = "https://account.anta.wire.link/",
            webSocket = "https://nginz-ssl.anta.wire.link/",
            blackList = "https://clientblacklist.wire.com/staging",
            teams = "https://teams.anta.wire.link/",
            website = "https://example.org",
            title = "Anta Backend",
            isOnPremises = true,
            apiProxy = null
        )
    }

}

expect fun fileLogger(filePath: String): LogWriter

expect fun homeDirectory(): String

expect fun coreLogic(rootPath: String, kaliumConfigs: KaliumConfigs): CoreLogic
