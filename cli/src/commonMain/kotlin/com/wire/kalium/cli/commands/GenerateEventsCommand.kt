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
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedClientID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.sync.EventGenerator
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString

class GenerateEventsCommand : CliktCommand(name = "generate-events") {

    private val userSession by requireObject<UserSessionScope>()
    private val targetUserId: String by option(help = "Target User which events will be generator for.").required()
    private val targetClientId: String by option(help = "Target Client which events will be generator for.").required()
    private val conversationId: String by option(help = "Target conversation which which will receive the events").required()
    private val eventLimit: Int by argument("Number of events to generate").int()
    private val outputFile: String by argument("Output file for the generated events")

    private var json = Json {
        prettyPrint = true
    }
    
    override fun run() = runBlocking {
        val selfUserId = userSession.users.getSelfUser().first().id
        val targetUserId = UserId(value = targetUserId, domain = selfUserId.domain)
        val targetClientId = ClientId(targetClientId)

        userSession.debug.establishSession(
            userId = targetUserId,
            clientId = targetClientId
        )
        val generator = EventGenerator(
            selfUserID = selfUserId,
            targetClient = QualifiedClientID(clientId = targetClientId, userId = targetUserId),
            proteusClient = userSession.proteusClientProvider.getOrCreate()
        )
        val events = generator.generateEvents(
            limit = eventLimit,
            conversationId = ConversationId(conversationId, domain = selfUserId.domain)
        )
        val response = NotificationResponse(
            time = Clock.System.now().toString(),
            hasMore = false,
            notifications = events.toList()
        )

        val sink = SystemFileSystem.sink(Path(outputFile)).buffered()
        val buffer = Buffer()
        buffer.writeString(json.encodeToString(response))
        sink.write(buffer, buffer.size)
        sink.close()

        echo("Generated ${eventLimit} event(s) written into $outputFile")
    }
}

