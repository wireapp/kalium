package com.wire.kalium.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.requireObject
import com.wire.kalium.cli.selectConnection
import com.wire.kalium.cli.selectConversation
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.conversation.AddMemberToConversationUseCase
import kotlinx.coroutines.runBlocking

class AddMemberToGroupCommand : CliktCommand(name = "add-member") {

    private val userSession by requireObject<UserSessionScope>()

    override fun run(): Unit = runBlocking {
        val selectedConversation = userSession.selectConversation()
        val selectedConnection = userSession.selectConnection()
        val result = userSession.conversations.addMemberToConversationUseCase(
            selectedConversation.id,
            listOf(selectedConnection.id)
        )

        when (result) {
            is AddMemberToConversationUseCase.Result.Success -> echo("Added user successfully")
            is AddMemberToConversationUseCase.Result.Failure -> throw PrintMessage("Add user failed: $result")
        }
    }
}
