package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.network.NetworkModule
import com.wire.kalium.network.api.user.login.LoginWithEmailRequest
import kotlinx.coroutines.runBlocking

class ConversationsApplication : CliktCommand() {
    private val email: String by option(help = "wire account email").required()
    private val password: String by option(help = "wire account password").required()

    override fun run(): Unit = runBlocking {

        val credentialsLedger = InMemoryCredentialsLedger()
        val networkModule = NetworkModule(credentialsLedger)

        val loginResult = networkModule.loginApi.emailLogin(
            LoginWithEmailRequest(email = email, password = password, label = "ktor"),
            false
        ).resultBody

        credentialsLedger.onAuthenticate(loginResult.accessToken, "") //TODO extract refresh token from cookie response

        val conversations = networkModule.conversationApi.conversationsByBatch(null, 100).resultBody.conversations

        println("Your conversations:")
        conversations.forEach {
            println("ID:${it.id}, Name: ${it.name}")
        }
    }

}

fun main(args: Array<String>) = ConversationsApplication().main(args)
