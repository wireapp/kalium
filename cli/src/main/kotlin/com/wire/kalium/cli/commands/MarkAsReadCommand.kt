package com.wire.kalium.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.wire.kalium.cli.selectConversation
import com.wire.kalium.logic.feature.UserSessionScope
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock

class MarkAsReadCommand : CliktCommand(name = "mark-as-read", help = "Mark a conversation as read") {

    private val userSession by requireObject<UserSessionScope>()

    override fun run(): Unit = runBlocking {
        val selectedConversation = userSession.selectConversation()
        val result = userSession.conversations.updateConversationReadDateUseCase(
            selectedConversation.id,
            Clock.System.now()
        )
        echo("Marked conversation as read")
    }
}
