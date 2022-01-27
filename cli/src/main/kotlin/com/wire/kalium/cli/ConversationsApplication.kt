package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ConversationsApplication : CliktCommand() {
    private val email: String by option(help = "wire account email").required()
    private val password: String by option(help = "wire account password").required()

    override fun run(): Unit = runBlocking {

        val coreLogic = CoreLogic("Sample Kalium CLI App", ".proteus")

        val session = coreLogic.authenticationScope {
            val result = loginUsingEmail(email, password, false)

            if (result !is AuthenticationResult.Success) {
                throw RuntimeException(
                    "There was an error on the login :(" +
                            "Check the credentials and the internet connection and try again"
                )
            }
            result.userSession
        }

        coreLogic.sessionScope(session) {
            val conversations = conversations.getConversations().first()
            println("Your conversations:")
            conversations.forEach {
                println("ID:${it.id}, Name: ${it.name}")
            }
        }
    }
}

fun main(args: Array<String>) = ConversationsApplication().main(args)
