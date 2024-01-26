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
package com.wire.kalium.monkeys.server

import co.touchlab.kermit.LogWriter
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.monkeys.conversation.Monkey
import com.wire.kalium.monkeys.coreLogic
import com.wire.kalium.monkeys.fileLogger
import com.wire.kalium.monkeys.homeDirectory
import com.wire.kalium.monkeys.model.Backend
import com.wire.kalium.monkeys.model.BackendConfig
import com.wire.kalium.monkeys.model.MonkeyId
import com.wire.kalium.monkeys.model.Team
import com.wire.kalium.monkeys.model.UserData
import com.wire.kalium.monkeys.model.basicHttpClient
import com.wire.kalium.monkeys.server.routes.configureAdministration
import com.wire.kalium.monkeys.server.routes.configureMonitoring
import com.wire.kalium.monkeys.server.routes.configureRoutes
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

class MonkeyServer : CliktCommand() {
    private val logLevel by option("-l", "--log-level", help = "log level").enum<KaliumLogLevel>().default(KaliumLogLevel.INFO)
    private val logOutputFile by option("-o", "--log-file", help = "output file for logs")
    private val fileLogger: LogWriter by lazy { fileLogger(logOutputFile ?: "kalium.log") }

    @OptIn(ExperimentalSerializationApi::class)
    val backendConfig by mutuallyExclusiveOptions<BackendConfig>(
        option(
            "-j",
            envvar = "JSON_BACKEND_CONFIG",
            help = "Json config from the backend",
        ).convert { Json.decodeFromString<BackendConfig>(it) },
        option(
            "-f", envvar = "FILE_BACKEND_CONFIG"
        ).file(mustExist = true, mustBeReadable = true).convert {
            Json.decodeFromStream(it.inputStream())
        },
    ).required()
    val coreLogic = coreLogic("${homeDirectory()}/.kalium/monkey")
    val monkey: Monkey by lazy {
        val presetTeam = backendConfig.presetTeam ?: error("Preset team must contain exact one user")
        val httpClient = basicHttpClient(backendConfig)
        val backend = Backend.fromConfig(backendConfig)
        val team = Team(
            backendConfig.teamName,
            presetTeam.id,
            backend,
            presetTeam.owner.email,
            backendConfig.passwordForUsers,
            UserId(presetTeam.owner.unqualifiedId, backendConfig.domain),
            httpClient
        )
        val userData = presetTeam.users.map { user ->
            UserData(
                user.email, backendConfig.passwordForUsers, UserId(user.unqualifiedId, backendConfig.domain), team
            )
        }.single()
        // currently the monkey id is not necessary in the server since the coordinator will be the one handling events for the replayer
        Monkey.internal(userData, MonkeyId.dummy())
    }


    override fun run() {
        if (logOutputFile != null) {
            CoreLogger.init(KaliumLogger.Config(logLevel, listOf(fileLogger)))
        } else {
            CoreLogger.init(KaliumLogger.Config(logLevel))
        }
        embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = {
            configureMonitoring()
            configureAdministration()
            configureRoutes(monkey, coreLogic)
        }).start(wait = true)
    }
}
