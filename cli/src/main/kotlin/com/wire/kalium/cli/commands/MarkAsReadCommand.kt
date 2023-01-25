/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.runBlocking

class MarkAsReadCommand : CliktCommand(name = "mark-as-read", help = "Mark a conversation as read") {

    private val userSession by requireObject<UserSessionScope>()

    override fun run(): Unit = runBlocking {
        val selectedConversation = userSession.selectConversation()
        val result = userSession.conversations.updateConversationReadDateUseCase(
            selectedConversation.id,
            DateTimeUtil.currentInstant()
        )
        echo("Marked conversation as read")
    }
}
