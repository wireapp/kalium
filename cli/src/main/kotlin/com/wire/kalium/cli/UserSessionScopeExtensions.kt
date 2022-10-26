package com.wire.kalium.cli

import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.output.TermUi
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.conversation.GetConversationsUseCase
import com.wire.kalium.logic.feature.publicuser.GetAllContactsResult
import kotlinx.coroutines.flow.first

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

suspend fun UserSessionScope.listConversations(): List<Conversation> {
    val conversations = getConversations(this)

    conversations.forEachIndexed { index, conversation ->
        TermUi.echo("$index) ${conversation.id.value}  Name: ${conversation.name}")
    }

    return conversations
}

suspend fun UserSessionScope.selectConversation(): Conversation {
    syncManager.waitUntilLive()

    val conversations = listConversations()

    val selectedConversationIndex =
        TermUi.prompt("Enter conversation index", promptSuffix = ": ")?.toInt() ?: throw PrintMessage("Index must be an integer")

    return conversations[selectedConversationIndex]
}

suspend fun UserSessionScope.selectMember(conversationId: ConversationId): User {

    val members = conversations.observeConversationMembers(conversationId).first()

    members.forEachIndexed { index, member ->
        TermUi.echo("$index) ${member.user.id.value} Name: ${member.user.name}")
    }

    val selectedMemberIndex =
        TermUi.prompt("Enter member index", promptSuffix = ": ")?.toInt() ?: throw PrintMessage("Index must be an integer")

    return members[selectedMemberIndex].user
}

suspend fun UserSessionScope.selectConnection(): OtherUser {
    val connections = users.getAllKnownUsers().first().let {
        when (it) {
            is GetAllContactsResult.Failure -> throw PrintMessage("Failed to retrieve connections: ${it.storageFailure}")
            is GetAllContactsResult.Success -> it.allContacts
        }
    }

    connections.forEachIndexed { index, connection ->
        TermUi.echo("$index) ${connection.id.value}  Name: ${connection.name}")
    }
    val selectedConnectionIndex =
        TermUi.prompt("Enter connection index", promptSuffix = ": ")?.toInt() ?: throw PrintMessage("Index must be an integer")
    return connections[selectedConnectionIndex]
}
