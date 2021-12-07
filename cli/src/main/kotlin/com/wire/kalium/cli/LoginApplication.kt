package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.network.NetworkModule
import com.wire.kalium.network.api.user.login.LoginWithEmailRequest
import kotlinx.coroutines.runBlocking

class LoginApplication : CliktCommand() {
    private val email: String by option(help = "wire account email").required()
    private val password: String by option(help = "wire account password").required()

    override fun run(): Unit = runBlocking {

        val credentialsLedger = InMemoryCredentialsLedger()
        val networkModule = NetworkModule(credentialsLedger)

        val loginResult = networkModule.loginApi.emailLogin(
            LoginWithEmailRequest(email = email, password = password, label = "ktor"),
            false
        ).resultBody

        println("Authenticated: LoginResult = $loginResult")
    }

}

fun main(args: Array<String>) = LoginApplication().main(args)
