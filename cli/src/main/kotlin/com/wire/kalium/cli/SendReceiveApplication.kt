package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.conversation.ConverationOptions
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.conversation.GetConversationsUseCase
import com.wire.kalium.logic.feature.session.CurrentSessionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SendReceiveApplication : CliktCommand() {

    private val email: String by option(help = "Wire account email").required()
    private val password: String by option(help = "Wire account password").required()
    private val environment: String? by option(help = "Choose backend environment: can be production or staging")
    private val coreLogic = CoreLogic("Kalium CLI", "$HOME_DIRECTORY/.kalium/accounts")

    private val serverConfig: ServerConfig by lazy {
        if (environment == "production") {
            ServerConfig.PRODUCTION
        } else {
            ServerConfig.DEFAULT
        }
    }

    override fun run() = runBlocking {
        CoreLogger.setLoggingLevel(KaliumLogLevel.DEBUG)

        val authSession = restoreSession() ?: login(email, password)
        val userSession = coreLogic.getSessionScope(authSession.userId)

        if (userSession.client.needsToRegisterClient()) {
            when (userSession.client.register(password, emptyList())) {
                is RegisterClientResult.Failure -> throw RuntimeException("Client registration failed")
                is RegisterClientResult.Success -> Unit
            }
        }

        val conversations = userSession.conversations.getConversations().let {
            when(it) {
                is GetConversationsUseCase.Result.Failure -> {
                    echo(it.storageFailure)
                    return@runBlocking
                }
                is GetConversationsUseCase.Result.Success -> it.convFlow.first()
            }
        }

        conversations.forEachIndexed { index, conversation ->
            println("$index) ${conversation.id.value}  Name: ${conversation.name}")
        }
        
        print("Enter conversation index: ")
        val conversationIndex = readLine()!!.toInt()
        val conversationID = conversations.get(conversationIndex).id

        launch(Dispatchers.Default) {
            userSession.listenToEvents()
        }

        launch(Dispatchers.Default) {
            userSession.messages.getRecentMessages(conversationID, limit = 1).collect {
                for (message in it) {
                    when (val content = message.content) {
                        is MessageContent.Text -> println("> ${content.value}")
                        MessageContent.Unknown -> { /* do nothing */ }
                    }
                }
            }

        }

        while (true) {
            val message = readLine()!!
            userSession.messages.sendTextMessage(conversationID, message)
        }
    }

    private suspend fun restoreSession(): AuthSession? {
        return coreLogic.authenticationScope {
            when (val currentSessionResult = session.currentSession()) {
                is CurrentSessionResult.Success -> currentSessionResult.authSession
                else -> null
            }
        }
    }

    private suspend fun login(username: String, password: String): AuthSession {
       return  coreLogic.authenticationScope {
           val loginResult = login(username, password, true, serverConfig)
           if (loginResult !is AuthenticationResult.Success) {
               throw RuntimeException("Login failed, check your credentials")
           }

           val addAccountResult = addAuthenticatedAccount(loginResult.userSession, true)
           if (addAccountResult !is AddAuthenticatedUserUseCase.Result.Success) {
                throw RuntimeException("Failed to save session")
           }
           loginResult.userSession
        }
    }

    companion object {
        val HOME_DIRECTORY: String = System.getProperty("user.home")
    }

}

fun main(args: Array<String>) = SendReceiveApplication().main(args)
