package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.network.api.SessionCredentials
import com.wire.kalium.network.api.user.login.LoginWithEmailRequest
import com.wire.kalium.network.utils.isSuccessful
import kotlinx.coroutines.runBlocking

class ConversationsApplication : CliktCommand() {
    private val email: String by option(help = "wire account email").required()
    private val password: String by option(help = "wire account password").required()

    override fun run(): Unit = runBlocking {

        val loginContainer = LoginNetworkContainer()

        val loginResult = loginContainer.loginApi.emailLogin(
            LoginWithEmailRequest(email = email, password = password, label = "ktor"),
            false
        )

        if (!loginResult.isSuccessful()) {
            println("There was an error on the login :( check the credentials and the internet connection and try again please")
        } else {
            val sessionData = loginResult.value
            //TODO: Get them 🍪 refresh token
            val sessionCredentials = SessionCredentials(sessionData.tokenType, sessionData.accessToken, "refreshToken")
            val networkModule = AuthenticatedNetworkContainer(sessionCredentials)
            val conversationsResponse = networkModule.conversationApi.conversationsByBatch(null, 100)

            if (!conversationsResponse.isSuccessful()) {
                println("There was an error loading the conversations :( check the internet connection and try again please")
            } else {
                println("Your conversations:")
                conversationsResponse.value.conversations.forEach {
                    println("ID:${it.id}, Name: ${it.name}")
                }
            }
        }
    }

}

fun main(args: Array<String>) = ConversationsApplication().main(args)
