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
