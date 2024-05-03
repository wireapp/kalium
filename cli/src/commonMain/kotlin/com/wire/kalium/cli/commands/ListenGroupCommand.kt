/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.wire.kalium.cli.selectConversation
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.feature.UserSessionScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ListenGroupCommand : CliktCommand(name = "listen-group") {

    private val userSession by requireObject<UserSessionScope>()

    override fun run() = runBlocking {
        val conversationID = userSession.selectConversation().id

        GlobalScope.launch(Dispatchers.Default) {
            userSession.messages.getRecentMessages(conversationID, limit = 1).collect {
                for (message in it) {
                    when (val content = message.content) {
                        is MessageContent.Text -> echo("> ${content.value}")
                        is MessageContent.Unknown -> { /* do nothing */ }
                        else -> { /* Do Nothing */ }
                    }
                }
            }
        }

        while (true) {
            val message = readln()
            userSession.messages.sendTextMessage(conversationID, message)
        }
    }
}
