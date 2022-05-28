package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.feature.register.RegisterParam
import com.wire.kalium.logic.feature.register.RegisterResult
import com.wire.kalium.logic.feature.register.RequestActivationCodeResult
import com.wire.kalium.network.NetworkLogger
import kotlinx.coroutines.runBlocking
import java.util.Scanner
import kotlin.properties.Delegates

class RegisterAccountApp : CliktCommand() {

    private val environment: String? by option(help = "Choose backend environment: can be production or staging")
    private val coreLogic = CoreLogic("Kalium CLI", ".proteus")

    private val serverConfig: ServerConfig.Links by lazy {
        if (environment == "production") {
            ServerConfig.PRODUCTION
        } else {
            ServerConfig.STAGING
        }
    }
    private var email: String = "email"
    private var code by Delegates.notNull<Int>()

    override fun run(): Unit = runBlocking {
        NetworkLogger.setLoggingLevel(KaliumLogLevel.DEBUG)
        when (val requestResult = requestCode()) {
            is RequestActivationCodeResult.Failure.Generic -> {
                echo(requestResult.failure)
                return@runBlocking
            }
            RequestActivationCodeResult.Success -> {
                when (val result = register()) {
                    is RegisterResult.Failure.Generic -> {
                        echo(result.failure)
                        return@runBlocking
                    }
                    is RegisterResult.Success -> {
                        echo(result.value)
                        return@runBlocking
                    }
                }
            }
        }
    }

    private suspend fun requestCode() = coreLogic.authenticationScope(serverConfig) {
        register.requestActivationCode(email)
    }

    private suspend fun activate() = coreLogic.authenticationScope(serverConfig) {
        val reader = Scanner(System.`in`)
        echo("Enter the activation code: ")
        code = reader.nextInt()
        register.activate(email, code.toString())
    }

    private suspend fun register() = coreLogic.authenticationScope(serverConfig) {
        val reader = Scanner(System.`in`)
        echo("Enter the activation code: ")
        code = reader.nextInt()
        val param = RegisterParam.PrivateAccount(
            "test",
            "test",
            email = email,
            emailActivationCode = code.toString(),
            password = "@Password123"
        )
        register.register(param)
    }


}

fun main(args: Array<String>) = RegisterAccountApp().main(args)
