package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.TermUi.echo
import com.github.ajalt.clikt.output.TermUi.prompt
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int

import com.sun.net.httpserver.HttpServer

import com.wire.kalium.logger.FileLogger
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.feature.client.DeleteClientResult
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.client.RegisterClientUseCase.RegisterClientParam
import com.wire.kalium.logic.feature.client.SelfClientsResult
import com.wire.kalium.logic.feature.conversation.AddMemberToConversationUseCase
import com.wire.kalium.logic.feature.conversation.CreateGroupConversationUseCase
import com.wire.kalium.logic.feature.conversation.GetConversationsUseCase
import com.wire.kalium.logic.feature.conversation.RemoveMemberFromConversationUseCase
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesResult
import com.wire.kalium.logic.feature.publicuser.GetAllContactsResult
import com.wire.kalium.logic.feature.server.GetServerConfigResult
import com.wire.kalium.logic.featureFlags.KaliumConfigs

import java.io.File
import java.net.InetSocketAddress
import java.util.Scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

suspend fun getConversations(userSession: UserSessionScope): List<Conversation> {
    userSession.syncManager.waitUntilLive()

    val conversations = userSession.conversations.getConversations().let {
        when (it) {
            is GetConversationsUseCase.Result.Success -> it.convFlow.first()
            else -> throw PrintMessage("Failed to retrieve conversation: $it")
        }
    }

    return conversations
}

suspend fun listConversations(userSession: UserSessionScope): List<Conversation> {
    val conversations = getConversations(userSession)

    conversations.forEachIndexed { index, conversation ->
        echo("$index) ${conversation.id.value}  Name: ${conversation.name}")
    }

    return conversations
}

suspend fun selectConversation(userSession: UserSessionScope): Conversation {
    userSession.syncManager.waitUntilLive()

    val conversations = listConversations(userSession)

    val selectedConversationIndex =
        prompt("Enter conversation index", promptSuffix = ": ")?.toInt() ?: throw PrintMessage("Index must be an integer")

    return conversations[selectedConversationIndex]
}


suspend fun selectMember(userSession: UserSessionScope, conversationId: ConversationId): User {

    val members = userSession.conversations.observeConversationMembers(conversationId).first()

    members.forEachIndexed { index, member ->
        echo("$index) ${member.user.id.value} Name: ${member.user.name}")
    }

    val selectedMemberIndex =
        prompt("Enter member index", promptSuffix = ": ")?.toInt() ?: throw PrintMessage("Index must be an integer")

    return members[selectedMemberIndex].user
}

suspend fun selectConnection(userSession: UserSessionScope): OtherUser {
    val connections = userSession.users.getAllKnownUsers().first().let {
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

    private val userSession by requireObject<UserSessionScope>()
    private val password: String by option(help = "Account password").prompt("password", promptSuffix = ": ", hideInput = true)

    override fun run() = runBlocking {
        val selfClientsResult = userSession.client.selfClients()

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

    private val userSession by requireObject<UserSessionScope>()
    private val name: String by option(help = "Name of the group").prompt()
    private val protocol: ConversationOptions.Protocol
            by option(help = "Protocol for sending messages").enum<ConversationOptions.Protocol>().default(ConversationOptions.Protocol.MLS)

    override fun run() = runBlocking {
        val users = userSession.users.getAllKnownUsers().first().let {
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
            ConversationOptions(protocol = protocol)
        )
        when (result) {
            is CreateGroupConversationUseCase.Result.Success -> echo("group created successfully")
            else -> throw PrintMessage("group creation failed: $result")
        }
    }

}

class LoginCommand : CliktCommand(name = "login") {

    private val coreLogic by requireObject<CoreLogic>()
    private var userSession: UserSessionScope? = null
    private val email: String by option("-e", "--email", help = "Account email").prompt("email", promptSuffix = ": ")
    private val password: String by option("-p", "--password", help = "Account password").prompt(
        "password",
        promptSuffix = ": ",
        hideInput = true
    )
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

    override fun run(): Unit = runBlocking {
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

        userSession = currentContext.findOrSetObject { coreLogic.getSessionScope(userId) }
    }
}

class ListenGroupCommand : CliktCommand(name = "listen-group") {

    private val userSession by requireObject<UserSessionScope>()

    override fun run() = runBlocking {
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

    private val userSession by requireObject<UserSessionScope>()

    override fun run(): Unit = runBlocking {
        val selectedConversation = selectConversation(userSession)
        val selectedConnection = selectConnection(userSession)

        when (val result = userSession.conversations.addMemberToConversationUseCase(
            selectedConversation.id,
            listOf(selectedConnection.id)
        )) {
            is AddMemberToConversationUseCase.Result.Success -> echo("Added user successfully")
            is AddMemberToConversationUseCase.Result.Failure -> throw PrintMessage("Add user failed: $result")
        }
    }
}

class RemoveMemberFromGroupCommand : CliktCommand(name = "remove-member") {

    private val userSession by requireObject<UserSessionScope>()

    override fun run(): Unit = runBlocking {
        val selectedConversation = selectConversation(userSession)
        val selectedMember = selectMember(userSession, selectedConversation.id)

        when (val result = userSession.conversations.removeMemberFromConversation(
            selectedConversation.id,
            selectedMember.id
        )) {
            is RemoveMemberFromConversationUseCase.Result.Success -> echo("Removed user successfully")
            is RemoveMemberFromConversationUseCase.Result.Failure -> throw PrintMessage("Remove user failed: $result")
        }
    }
}

class RefillKeyPackagesCommand : CliktCommand(name = "refill-key-packages") {

    private val userSession by requireObject<UserSessionScope>()

    override fun run() = runBlocking {
        when (val result = userSession.client.refillKeyPackages()) {
            is RefillKeyPackagesResult.Success -> echo("key packages were refilled")
            is RefillKeyPackagesResult.Failure -> throw PrintMessage("refill key packages failed: ${result.failure}")
        }
    }
}

class ConsoleContext(
    var currentConversation: Conversation?,
    var isMuted: Boolean = false
)

class KeyStroke(
    val key: Char,
    val handler: suspend (userSession: UserSessionScope, context: ConsoleContext) -> Int
) {
    suspend fun exec(userSession: UserSessionScope, context: ConsoleContext) = handler(userSession, context)
}

var strokes: Array<KeyStroke> = arrayOf(
    KeyStroke('l', ::listConversationsHandler),
    KeyStroke('c', ::startCallHandler),
    KeyStroke('a', ::answerCallHandler),
    KeyStroke('e', ::endCallHandler),
    KeyStroke('m', ::muteCallHandler),
    KeyStroke('s', ::selectConversationHandler),
    KeyStroke('q', ::quitApplication)
)

suspend fun executeStroke(userSession: UserSessionScope, context: ConsoleContext,  key: Char) {
    for (stroke in strokes) {
        if (stroke.key.equals(key)) {
            stroke.handler(userSession, context)
            return
        }
    }
    echo("Unknown stroke: ${key}")
}

suspend fun listConversationsHandler(userSession: UserSessionScope, context: ConsoleContext): Int {
    listConversations(userSession)
    return 0
}

suspend fun selectConversationHandler(userSession: UserSessionScope, context: ConsoleContext): Int {
    context.currentConversation = selectConversation(userSession)
    return 0
}

suspend fun startCallHandler(userSession: UserSessionScope, context: ConsoleContext): Int {
    val currentConversation = context.currentConversation ?: return -1

    val convType = when (currentConversation.type) {
        Conversation.Type.ONE_ON_ONE -> ConversationType.OneOnOne
        Conversation.Type.GROUP -> ConversationType.Conference
        else -> ConversationType.Unknown
    }

    userSession.calls.startCall.invoke(
        conversationId = currentConversation.id,
        conversationType = convType
    )

    return 0
}

suspend fun answerCallHandler(userSession: UserSessionScope, context: ConsoleContext): Int {
    val currentConversation = context.currentConversation ?: return -1
    userSession.calls.answerCall.invoke(conversationId = currentConversation.id)
    return 0
}


suspend fun endCallHandler(userSession: UserSessionScope, context: ConsoleContext): Int {
    val currentConversation = context.currentConversation ?: return -1
    userSession.calls.endCall.invoke(conversationId = currentConversation.id)
    return 0
}

suspend fun muteCallHandler(userSession: UserSessionScope, context: ConsoleContext): Int {
    val currentConversation = context.currentConversation ?: return -1

    context.isMuted = !context.isMuted

    if (context.isMuted)
        userSession.calls.muteCall(conversationId = currentConversation.id)
    else
        userSession.calls.unMuteCall(conversationId = currentConversation.id)

    return 0
}

suspend fun quitApplication(userSession: UserSessionScope, context: ConsoleContext): Int {
    kotlin.system.exitProcess(0)
    return 0
}


class ConsoleCommand : CliktCommand(name = "console") {
    private val port by option(help = "REST API server port").int().default(0)
    private val avsTest by option("-T").flag(default = false)
    private val avsNoise by option("-N").flag(default = false)

    private val userSession by requireObject<UserSessionScope>()
    private val context = ConsoleContext(null, false)

    override fun run() = runBlocking {
        val conversations = getConversations(userSession)
        context.currentConversation = conversations[0]

        if (port > 0) {
            HttpServer.create(InetSocketAddress(port), 0).apply {
                createContext("/stroke") { http ->
                    val stroke = http.getRequestURI().getQuery()[0]
                    echo("*** REST-stroke=${stroke}")
                    val job = GlobalScope.launch(Dispatchers.Default) {
                        executeStroke(userSession, context, stroke);
                    }
                    http.responseHeaders.add("Content-type", "text/plain")
                    http.sendResponseHeaders(200, 0)
                    val os = http.getResponseBody()
                    // We should get the response from the stroke here....
                    // and send it on the os...
                    os.close()
                }
                createContext("/command") { http ->
                    val command = http.getRequestURI().getQuery()
                    echo("*** REST-COMMAND=${command}")
                    val job = GlobalScope.launch(Dispatchers.Default) {
                        // executeCommand(userSession, stroke);
                    }
                    http.responseHeaders.add("Content-type", "text/plain")
                    http.sendResponseHeaders(200, 0)
                    val os = http.getResponseBody()
                    // We should get the response from the command here....
                    // and send it on the os...
                    os.close()
                }
                start()
            }
        }

        var avsFlags: Int = 0
        if (avsTest)
            avsFlags = 2
        if (avsNoise)
            avsFlags = 8

        while (true) {
            val scanner = Scanner(System.`in`)
            val stroke = scanner.next().single()

            echo("stroke: ${stroke}")

            val job = GlobalScope.launch(Dispatchers.Default) {
                executeStroke(userSession, context, stroke);
            }
            job.join()
        }
    }
}


class CLIApplication : CliktCommand(allowMultipleSubcommands = true) {

    private val logLevel by option(help = "log level").enum<KaliumLogLevel>().default(KaliumLogLevel.WARN)
    private val logOutputFile by option(help = "output file for logs").file(canBeDir = false)
    private val developmentApiEnabled by option(help = "use development API if supported by backend").flag(default = false)
    private val fileLogger: FileLogger by lazy { FileLogger(logOutputFile ?: File("kalium.log")) }

    override fun run() = runBlocking {
        currentContext.findOrSetObject {
            CoreLogic(
                clientLabel = "Kalium CLI",
                rootPath = "$HOME_DIRECTORY/.kalium/accounts",
                kaliumConfigs = KaliumConfigs(developmentApiEnabled = developmentApiEnabled)
            )
        }

        if (logOutputFile != null) {
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
    LoginCommand().subcommands(
        CreateGroupCommand(),
        ListenGroupCommand(),
        DeleteClientCommand(),
        AddMemberToGroupCommand(),
        RemoveMemberFromGroupCommand(),
        ConsoleCommand(),
        RefillKeyPackagesCommand()
    )
).main(args)
