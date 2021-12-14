package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

class ConversationsApplication : CliktCommand() {
    private val email: String by option(help = "wire account email").required()
    private val password: String by option(help = "wire account password").required()

    override fun run(): Unit = runBlocking {

        val rootProteusFolder = File("proteus").also { it.mkdirs() }
        val core = CoreLogic("Kalium JVM CLI sample on ${System.getProperty("os.name")}", rootProteusFolder.path)
        val loginResult = core.authenticationScope {
            loginUsingEmail(email, password, shouldPersistClient = false)
        }

        if (loginResult !is AuthenticationScope.AuthenticationResult.Success) {
            println("Failure to authenticate: $loginResult")
            return@runBlocking
        }

        core.sessionScope(loginResult.userSession) {
            println("Your conversations:")
            val conversations = conversations.getConversations().first()

            conversations.forEach {
                println("ID:${it.id}, Name: ${it.name}")
            }

            val conversation = conversations.first()
            try {
                messages.sendTextMessage(conversation.id, "Hello, people in ${conversation.name}!")
            } catch (notImplemented: NotImplementedError) {
                /** But it will be! **/
            }
        }
    }

}

fun main(args: Array<String>) = ConversationsApplication().main(args)
