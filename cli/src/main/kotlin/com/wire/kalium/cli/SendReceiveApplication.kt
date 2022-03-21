package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.conversation.GetConversationsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SendReceiveApplication : CliktCommand() {

    private val email: String by option(help = "Wire account email").required()
    private val password: String by option(help = "Wire account password").required()
    private val environment: String? by option(help = "Choose backend environment: can be production or staging")
    private val coreLogic = CoreLogic("Kalium CLI", ".proteus")

    private val serverConfig: ServerConfig by lazy {
        if (environment == "production") {
            ServerConfig.PRODUCTION
        } else {
            ServerConfig.DEFAULT
        }
    }

    override fun run() = runBlocking {
        val authSession = login(email, password)
        val userSession = coreLogic.getSessionScope(authSession)

        when (userSession.client.register(password, emptyList())) {
            is RegisterClientResult.Failure -> throw RuntimeException("Client registration failed")
            is RegisterClientResult.Success -> Unit
        }

        val conversations = userSession.conversations.getConversations().let {
            when(it) {
                is GetConversationsUseCase.Result.Failure -> {
                    echo(it.storageFailure.rootCause)
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

    private suspend fun login(username: String, password: String): AuthSession {
        val result = coreLogic.getAuthenticationScope().login(username, password, false, serverConfig)

        if (result !is AuthenticationResult.Success) {
            throw RuntimeException(
                "There was an error on the login :(" +
                        "Check the credentials and the internet connection and try again"
            )
        }

        return result.userSession
    }

}

fun main(args: Array<String>) = SendReceiveApplication().main(args)
