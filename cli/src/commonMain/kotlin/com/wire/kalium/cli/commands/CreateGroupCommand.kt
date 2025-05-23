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
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.types.enum
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.conversation.createconversation.ConversationCreationResult
import com.wire.kalium.logic.feature.publicuser.GetAllContactsResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class CreateGroupCommand : CliktCommand(name = "create-group") {

    private val userSession by requireObject<UserSessionScope>()
    private val name: String by option(help = "Name of the group").prompt()
    private val protocol: CreateConversationParam.Protocol
            by option(help = "Protocol for sending messages").enum<CreateConversationParam.Protocol>().default(CreateConversationParam.Protocol.MLS)

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

        val userIndicesRaw = prompt("Enter user indexes", promptSuffix = ": ", default = "")
        val userIndices = userIndicesRaw?.split("\\s".toRegex())?.filter { it.isNotEmpty() }?.map(String::toInt) ?: emptyList()
        val userToAddList = userIndices.map { users[it].id }

        val result = userSession.conversations.createRegularGroup(
            name,
            userToAddList,
            CreateConversationParam(protocol = protocol)
        )
        when (result) {
            is ConversationCreationResult.Success -> echo("group created successfully")
            else -> throw PrintMessage("group creation failed: $result")
        }
    }

}
