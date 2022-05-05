package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.client.DeleteClientResult
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.client.SelfClientsResult
import com.wire.kalium.logic.feature.conversation.GetConversationsUseCase
import com.wire.kalium.logic.feature.session.CurrentSessionResult
import com.wire.kalium.logic.feature.session.GetAllSessionsResult
import com.wire.kalium.logic.functional.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.wire.kalium.logic.feature.client.RegisterClientUseCase.RegisterClientParam

private val coreLogic = CoreLogic("Kalium CLI", "${CLIApplication.HOME_DIRECTORY}/.kalium/accounts")

suspend fun restoreSession(): AuthSession? {
    return coreLogic.authenticationScope {
        when (val currentSessionResult = session.currentSession()) {
            is CurrentSessionResult.Success -> currentSessionResult.authSession
            else -> null
        }
    }
}

class DeleteClientCommand: CliktCommand(name = "delete-client") {

    private val password: String by option(help = "Account password").prompt("password", promptSuffix = ": ", hideInput = true)

    override fun run() = runBlocking {
        val authSession = restoreSession() ?: throw PrintMessage("no active session")
        val userSession = coreLogic.getSessionScope(authSession.userId)

        val selfClientsResult = userSession.client.selfClients()

        if (selfClientsResult !is SelfClientsResult.Success) {
            throw PrintMessage("failed to retrieve self clients")
        }

        selfClientsResult.clients.forEachIndexed { index, client ->
            echo("$index) ${client.model ?: "Unknown"}(${client.label ?: "-"}) ${client.registrationTime}")
        }

        val clientIndex = prompt("Enter client index", promptSuffix = ": ")?.toInt() ?: throw PrintMessage("Index must be an integer")
        val deleteClientResult =  userSession.client.deleteClient(DeleteClientParam(password, selfClientsResult.clients[clientIndex].clientId))

        when (deleteClientResult) {
            is DeleteClientResult.Failure.Generic -> throw PrintMessage("Delete client failed: ${deleteClientResult.genericFailure}")
            DeleteClientResult.Failure.InvalidCredentials -> throw PrintMessage("Invalid credentials")
            DeleteClientResult.Success -> echo("Client successfully deleted")
        }
    }

}

class CreateGroupCommand : CliktCommand(name = "create-group") {

    private val name: String by option(help = "Name of the group").prompt()

    override fun run() = runBlocking {
        val authSession = restoreSession() ?: throw PrintMessage("no active session")
        val userSession = coreLogic.getSessionScope(authSession.userId)

        val users = userSession.users.getAllKnownUsers()

        users.forEachIndexed { index, user ->
            echo("$index) ${user.id.value}  Name: ${user.name}")
        }

        val userIndicesRaw = prompt("Enter user indexes", promptSuffix = ": ")
        val userIndices = userIndicesRaw?.split("\\s".toRegex())?.map(String::toInt) ?: emptyList()
        val members = userIndices.map { Member(users[it].id) }

        val result = userSession.conversations.createGroupConversation(
            name,
            members,
            ConversationOptions(protocol = ConversationOptions.Protocol.MLS)
        )
        when (result) {
            is Either.Right -> echo("group created successfully")
            is Either.Left -> throw PrintMessage("group creation failed: ${result.value}")
        }
    }

}

class LoginCommand: CliktCommand(name = "login") {

    private val email: String by option(help = "Account email").prompt("email", promptSuffix = ": ")
    private val password: String by option(help = "Account password").prompt("password", promptSuffix = ": ", hideInput = true)
    private val environment: String? by option(help = "Choose backend environment: can be production or staging")

    private val serverConfig: ServerConfig by lazy {
        if (environment == "production") {
            ServerConfig.PRODUCTION
        } else {
            ServerConfig.DEFAULT
        }
    }

    override fun run() = runBlocking {
        val authSession = coreLogic.authenticationScope {
            val loginResult = login(email, password, true, serverConfig)
            if (loginResult !is AuthenticationResult.Success) {
                throw PrintMessage("Login failed, check your credentials")
            }

            val allSessionsResult = this.session.allSessions()
            if (allSessionsResult !is GetAllSessionsResult.Success) {
                throw PrintMessage("Failed retrieve existing sessions")
            }

            if (allSessionsResult.sessions.map { it.userId }.contains(loginResult.userSession.userId)) {
                this.session.updateCurrentSession(loginResult.userSession.userId)
            } else {
                val addAccountResult = addAuthenticatedAccount(loginResult.userSession, true)
                if (addAccountResult !is AddAuthenticatedUserUseCase.Result.Success) {
                    throw PrintMessage("Failed to save session")
                }
            }

            loginResult.userSession
        }

        coreLogic.sessionScope(authSession.userId) {
            if (client.needsToRegisterClient()) {
                when (client.register(RegisterClientParam.ClientWithoutToken(password, emptyList()))) {
                    is RegisterClientResult.Failure -> throw PrintMessage("Client registration failed")
                    is RegisterClientResult.Success -> echo("Login successful")
                }
            }
        }
    }
}

class ListenGroupCommand: CliktCommand(name = "listen-group") {

    override fun run() = runBlocking {
        val authSession = restoreSession() ?: throw PrintMessage("no active session")
        val userSession = coreLogic.getSessionScope(authSession.userId)
        val conversations = userSession.conversations.getConversations().let {
            when (it) {
                is GetConversationsUseCase.Result.Success -> it.convFlow.first()
                is GetConversationsUseCase.Result.Failure -> throw PrintMessage("Failed to retrieve conversation: ${it.storageFailure}")
                else -> throw PrintMessage("Failed to retrieve conversation: Unknown reason")
            }
        }

        conversations.forEachIndexed { index, conversation ->
            echo("$index) ${conversation.id.value}  Name: ${conversation.name}")
        }

        val conversationIndex = prompt("Enter conversation index", promptSuffix = ": ")?.toInt() ?: throw PrintMessage("Index must be an integer")
        val conversationID = conversations[conversationIndex].id

        GlobalScope.launch(Dispatchers.Default) {
            userSession.listenToEvents()
        }

        GlobalScope.launch(Dispatchers.Default) {
            userSession.messages.getRecentMessages(conversationID, limit = 1).collect {
                for (message in it) {
                    when (val content = message.content) {
                        is MessageContent.Text -> echo("> ${content.value}")
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

}

class CLIApplication : CliktCommand(allowMultipleSubcommands = true) {

    override fun run() = runBlocking {
        CoreLogger.setLoggingLevel(KaliumLogLevel.DEBUG)
    }

    companion object {
        val HOME_DIRECTORY: String = System.getProperty("user.home")
    }

}

fun main(args: Array<String>) = CLIApplication()
    .subcommands(LoginCommand(), CreateGroupCommand(), ListenGroupCommand(), DeleteClientCommand())
    .main(args)
