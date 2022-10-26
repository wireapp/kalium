package com.wire.kalium.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.requireObject
import com.wire.kalium.cli.selectConversation
import com.wire.kalium.cli.selectMember
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.conversation.RemoveMemberFromConversationUseCase
import kotlinx.coroutines.runBlocking

class RemoveMemberFromGroupCommand : CliktCommand(name = "remove-member") {

    private val userSession by requireObject<UserSessionScope>()

    override fun run(): Unit = runBlocking {
        val selectedConversation = userSession.selectConversation()
        val selectedMember = userSession.selectMember(selectedConversation.id)

        when (val result = userSession.conversations.removeMemberFromConversation(selectedConversation.id, selectedMember.id)) {
            is RemoveMemberFromConversationUseCase.Result.Success -> echo("Removed user successfully")
            is RemoveMemberFromConversationUseCase.Result.Failure -> throw PrintMessage("Remove user failed: $result")
        }
    }
}
