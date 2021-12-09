package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.logic.AuthenticationScope
import com.wire.kalium.logic.CoreLogic
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

class ConversationsApplication : CliktCommand() {
    private val email: String by option(help = "wire account email").required()
    private val password: String by option(help = "wire account password").required()

    override fun run(): Unit = runBlocking {

        val core = CoreLogic()
        val loginResult = core.authenticationScope {
            loginUsingEmail(email, password)
        }

        if (loginResult !is AuthenticationScope.AuthenticationResult.Success) {
            println("Failure to authenticate: $loginResult")
            return@runBlocking
        }

        core.sessionScope(loginResult.userSession) {
            println("Your conversations:")
            conversations.getConversations().collect { conversations ->
                conversations.forEach {
                    println("ID:${it.id}, Name: ${it.name}")
                }
            }
        }
    }

}

fun main(args: Array<String>) = ConversationsApplication().main(args)
