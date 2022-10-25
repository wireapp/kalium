@file:Suppress("TooManyFunctions")
package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.TermUi.echo
import com.github.ajalt.clikt.output.TermUi.prompt
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.wire.kalium.logger.FileLogger
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.conversation.GetConversationsUseCase
import com.wire.kalium.logic.feature.publicuser.GetAllContactsResult
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

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
