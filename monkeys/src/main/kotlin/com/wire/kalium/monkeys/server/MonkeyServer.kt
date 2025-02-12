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
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.common.logger.CoreLogger
import com.wire.kalium.monkeys.coreLogic
import com.wire.kalium.monkeys.fileLogger
import com.wire.kalium.monkeys.homeDirectory
import com.wire.kalium.monkeys.model.BackendConfig
import com.wire.kalium.monkeys.server.routes.configureAdministration
import com.wire.kalium.monkeys.server.routes.configureMonitoring
import com.wire.kalium.monkeys.server.routes.configureRoutes
import com.wire.kalium.monkeys.server.routes.initMonkey
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

private const val DEFAULT_PORT = 8080

class MonkeyServer : CliktCommand() {
    private val port by option("-p", "--port", help = "Port to bind the http server").int().default(DEFAULT_PORT)
    private val logLevel by option("-l", "--log-level", help = "log level").enum<KaliumLogLevel>().default(KaliumLogLevel.INFO)
    private val logOutputFile by option("-o", "--log-file", help = "output file for logs")
    private val oldCode by option("-c", "--code", help = "Current 2FA code to use until a new one can be generated")
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
    )
    val coreLogic = coreLogic("${homeDirectory()}/.kalium/monkey")

    override fun run() {
        if (logOutputFile != null) {
            CoreLogger.init(KaliumLogger.Config(logLevel, listOf(fileLogger)))
        } else {
            CoreLogger.init(KaliumLogger.Config(logLevel))
        }
        backendConfig?.let { initMonkey(it, oldCode) }
        embeddedServer(Netty, port = port, host = "0.0.0.0", module = {
            configureMonitoring()
            configureAdministration()
            configureRoutes(coreLogic, oldCode)
        }).start(wait = true)
    }
}
