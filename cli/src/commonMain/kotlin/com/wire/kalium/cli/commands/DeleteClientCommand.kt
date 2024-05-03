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
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.client.DeleteClientResult
import com.wire.kalium.logic.feature.client.SelfClientsResult
import kotlinx.coroutines.runBlocking

class DeleteClientCommand : CliktCommand(name = "delete-client") {

    private val userSession by requireObject<UserSessionScope>()
    private val password: String by option(help = "Account password").prompt("password", promptSuffix = ": ", hideInput = true)

    override fun run() = runBlocking {
        val selfClientsResult = userSession.client.fetchSelfClients()

        if (selfClientsResult !is SelfClientsResult.Success) {
            throw PrintMessage("failed to retrieve self clients")
        }

        selfClientsResult.clients.forEachIndexed { index, client ->
            echo("$index) ${client.model ?: "Unknown"}(${client.label ?: "-"}) ${client.registrationTime}")
        }

        val clientIndex = prompt("Enter client index", promptSuffix = ": ")?.toInt() ?: throw PrintMessage("Index must be an integer")
        val deleteClientResult =
            userSession.client.deleteClient(DeleteClientParam(password, selfClientsResult.clients[clientIndex].id))

        when (deleteClientResult) {
            is DeleteClientResult.Failure.Generic -> throw PrintMessage("Delete client failed: ${deleteClientResult.genericFailure}")
            DeleteClientResult.Failure.InvalidCredentials -> throw PrintMessage("Invalid credentials")
            DeleteClientResult.Success -> echo("Client successfully deleted")
            DeleteClientResult.Failure.PasswordAuthRequired -> throw PrintMessage("Missing Password")
        }
    }
}
