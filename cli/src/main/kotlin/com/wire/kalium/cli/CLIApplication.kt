package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.TermUi.echo
import com.github.ajalt.clikt.output.TermUi.prompt
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.wire.kalium.logger.FileLogger
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.feature.client.DeleteClientResult
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.client.RegisterClientUseCase.RegisterClientParam
import com.wire.kalium.logic.feature.client.SelfClientsResult
import com.wire.kalium.logic.feature.conversation.CreateGroupConversationUseCase
import com.wire.kalium.logic.feature.conversation.GetConversationsUseCase
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesResult
import com.wire.kalium.logic.feature.publicuser.GetAllContactsResult
import com.wire.kalium.logic.feature.server.GetServerConfigResult
import com.wire.kalium.logic.feature.session.CurrentSessionResult
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

private val coreLogic = CoreLogic("Kalium CLI", "${CLIApplication.HOME_DIRECTORY}/.kalium/accounts", kaliumConfigs = KaliumConfigs())

fun restoreSession(): AccountInfo? {
    return coreLogic.globalScope {
        when (val currentSessionResult = session.currentSession()) {
            is CurrentSessionResult.Success -> currentSessionResult.accountInfo
            else -> null
        }
    }
}

fun currentUserSession(): UserSessionScope {
    val accountInfo = restoreSession() ?: throw PrintMessage("no active session")
    return coreLogic.getSessionScope(accountInfo.userId)
}

suspend fun selectConversation(userSession: UserSessionScope): Conversation {
    userSession.syncManager.waitUntilLive()

    val conversations = userSession.conversations.getConversations().let {
        when (it) {
            is GetConversationsUseCase.Result.Success -> it.convFlow.first()
            else -> throw PrintMessage("Failed to retrieve conversation: $it")
        }
    }

    conversations.forEachIndexed { index, conversation ->
        echo("$index) ${conversation.id.value}  Name: ${conversation.name}")
    }

    val selectedConversationIndex =
        prompt("Enter conversation index", promptSuffix = ": ")?.toInt() ?: throw PrintMessage("Index must be an integer")
    return conversations[selectedConversationIndex]
}

suspend fun selectConnection(userSession: UserSessionScope): OtherUser {
    val connections = userSession.users.getAllKnownUsers().let {
        when (it) {
            is GetAllContactsResult.Failure -> throw PrintMessage("Failed to retrieve connections: ${it.storageFailure}")
            is GetAllContactsResult.Success -> it.allContacts
        }
    }

    connections.forEachIndexed { index, connection ->
        echo("$index) ${connection.id.value}  Name: ${connection.name}")
    }
    val selectedConnectionIndex =
        prompt("Enter connection index", promptSuffix = ": ")?.toInt() ?: throw PrintMessage("Index must be an integer")
    return connections[selectedConnectionIndex]
}

class DeleteClientCommand : CliktCommand(name = "delete-client") {

    private val password: String by option(help = "Account password").prompt("password", promptSuffix = ": ", hideInput = true)

    override fun run() = runBlocking {
        val userSession = currentUserSession()

        val selfClientsResult = currentUserSession().client.selfClients()

        if (selfClientsResult !is SelfClientsResult.Success) {
            throw PrintMessage("failed to retrieve self clients")
        }

        selfClientsResult.clients.forEachIndexed { index, client ->
            echo("$index) ${client.model ?: "Unknown"}(${client.label ?: "-"}) ${client.registrationTime}")
        }

        val clientIndex = prompt("Enter client index", promptSuffix = ": ")?.toInt() ?: throw PrintMessage("Index must be an integer")
        val deleteClientResult =
            userSession.client.deleteClient(DeleteClientParam(password, selfClientsResult.clients[clientIndex].id))

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
        val userSession = currentUserSession()

        val users = userSession.users.getAllKnownUsers().let {
            when (it) {
                is GetAllContactsResult.Failure -> throw PrintMessage("Failed to retrieve connections: ${it.storageFailure}")
                is GetAllContactsResult.Success -> it.allContacts
            }
        }

        users.forEachIndexed { index, user ->
            echo("$index) ${user.id.value}  Name: ${user.name}")
        }

        val userIndicesRaw = prompt("Enter user indexes", promptSuffix = ": ")
        val userIndices = userIndicesRaw?.split("\\s".toRegex())?.map(String::toInt) ?: emptyList()
        val userToAddList = userIndices.map { users[it].id }

        val result = userSession.conversations.createGroupConversation(
            name,
            userToAddList,
            ConversationOptions(protocol = ConversationOptions.Protocol.MLS)
        )
        when (result) {
            is CreateGroupConversationUseCase.Result.Success -> echo("group created successfully")
            else -> throw PrintMessage("group creation failed: $result")
        }
    }

}

class LoginCommand : CliktCommand(name = "login") {

    private val email: String by option(help = "Account email").prompt("email", promptSuffix = ": ")
    private val password: String by option(help = "Account password").prompt("password", promptSuffix = ": ", hideInput = true)
    private val environment: String? by option(
        help = "Choose backend environment: can be production, staging or an URL to a server configuration"
    )

    private suspend fun serverConfig(): ServerConfig.Links {
        return environment?.let { env ->
            when (env) {
                "staging" -> ServerConfig.STAGING
                "production" -> ServerConfig.PRODUCTION
                else -> {
                    coreLogic.globalScope {
                        when (val result = fetchServerConfigFromDeepLink(env)) {
                            is GetServerConfigResult.Success -> result.serverConfigLinks
                            is GetServerConfigResult.Failure ->
                                throw PrintMessage("failed to fetch server config from: $env")
                        }
                    }
                }
            }
        } ?: ServerConfig.DEFAULT
    }

    private suspend fun provideVersionedAuthenticationScope(serverLinks: ServerConfig.Links): AuthenticationScope =
        when (val result = coreLogic.versionedAuthenticationScope(serverLinks).invoke()) {
            is AutoVersionAuthScopeUseCase.Result.Failure.Generic ->
                throw PrintMessage("failed to create authentication scope: ${result.genericFailure}")

            AutoVersionAuthScopeUseCase.Result.Failure.TooNewVersion ->
                throw PrintMessage("failed to create authentication scope: api version not supported")

            AutoVersionAuthScopeUseCase.Result.Failure.UnknownServerVersion ->
                throw PrintMessage("failed to create authentication scope: unknown server version")

            is AutoVersionAuthScopeUseCase.Result.Success -> result.authenticationScope
        }

    override fun run() = runBlocking {
        val loginResult = provideVersionedAuthenticationScope(serverConfig()).login(email, password, true).let {
            if (it !is AuthenticationResult.Success) {
                throw PrintMessage("Login failed, check your credentials")
            } else {
                it
            }
        }

        val userId = coreLogic.globalScope {
            addAuthenticatedAccount(loginResult.serverConfigId, loginResult.ssoID, loginResult.authData, true)
            loginResult.authData.userId
        }

        coreLogic.sessionScope(userId) {
            if (client.needsToRegisterClient()) {
                when (client.register(RegisterClientParam(password, emptyList()))) {
                    is RegisterClientResult.Failure -> throw PrintMessage("Client registration failed")
                    is RegisterClientResult.Success -> echo("Login successful")
                }
            }
        }
    }
}

class ListenGroupCommand : CliktCommand(name = "listen-group") {

    override fun run() = runBlocking {
        val userSession = currentUserSession()
        val conversationID = selectConversation(userSession).id

        GlobalScope.launch(Dispatchers.Default) {
            userSession.messages.getRecentMessages(conversationID, limit = 1).collect {
                for (message in it) {
                    when (val content = message.content) {
                        is MessageContent.Text -> echo("> ${content.value}")
                        is MessageContent.Unknown -> { /* do nothing */
                        }
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

class AddMemberToGroupCommand : CliktCommand(name = "add-member") {
    override fun run() = runBlocking {

        val userSession = currentUserSession()

        val selectedConversation = selectConversation(userSession)
        val selectedConnection = selectConnection(userSession)

        userSession.conversations.addMemberToConversationUseCase(selectedConversation.id, listOf(selectedConnection.id))
    }
}

class RefillKeyPackagesCommand : CliktCommand(name = "refill-key-packages") {
    override fun run() = runBlocking {
        val userSession = currentUserSession()

        when (val result = userSession.client.refillKeyPackages()) {
            is RefillKeyPackagesResult.Success -> echo("key packages were refilled")
            is RefillKeyPackagesResult.Failure -> throw PrintMessage("refill key packages failed: ${result.failure}")
        }
    }
}

class CLIApplication : CliktCommand(allowMultipleSubcommands = true) {

    private val logLevel by option(help = "log level").enum<KaliumLogLevel>().default(KaliumLogLevel.WARN)
    private val logFile by option(help = "output file for logs").file(canBeDir = false)

    private val fileLogger: FileLogger by lazy { FileLogger(logFile ?: File("kalium.log")) }

    override fun run() = runBlocking {
        if (logFile != null) {
            CoreLogger.setLoggingLevel(logLevel, fileLogger)
        } else {
            CoreLogger.setLoggingLevel(logLevel)
        }
    }

    companion object {
        val HOME_DIRECTORY: String = System.getProperty("user.home")
    }

}

fun main(args: Array<String>) = CLIApplication().subcommands(
    LoginCommand(),
    CreateGroupCommand(),
    ListenGroupCommand(),
    DeleteClientCommand(),
    AddMemberToGroupCommand(),
    RefillKeyPackagesCommand()
).main(args)
