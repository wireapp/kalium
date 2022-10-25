package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.feature.UserSessionScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
