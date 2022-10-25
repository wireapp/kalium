package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.types.enum
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.conversation.CreateGroupConversationUseCase
import com.wire.kalium.logic.feature.publicuser.GetAllContactsResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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
