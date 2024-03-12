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

@file:Suppress("TooManyFunctions")

package com.wire.kalium.monkeys

import co.touchlab.kermit.LogWriter
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.monkeys.actions.Action
import com.wire.kalium.monkeys.model.Event
import com.wire.kalium.monkeys.model.TestDataImporter
import com.wire.kalium.monkeys.model.UserData
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyConfig
import com.wire.kalium.monkeys.pool.MonkeyPool
import com.wire.kalium.monkeys.storage.EventStorage
import com.wire.kalium.monkeys.storage.FileStorage
import com.wire.kalium.monkeys.storage.PostgresStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import com.wire.kalium.monkeys.model.EventStorage.FileStorage as FileStorageConfig
import com.wire.kalium.monkeys.model.EventStorage.PostgresStorage as PostgresStorageConfig

enum class EventLogType {
    FILE, POSTGRES
}

class ReplayApplication : CliktCommand(allowMultipleSubcommands = true) {

    private val eventLogType by option("-s", help = "read from file or postgres").enum<EventLogType>().default(EventLogType.FILE).validate {
        when (it) {
            EventLogType.FILE -> require(eventsLocation != null && teamsLocation != null) { "-e and -t must be provided for the file type" }
            EventLogType.POSTGRES -> require(
                host.isNotBlank() && dbName.isNotBlank() && username.isNotBlank() && password.isNotBlank() && executionId != -1
            ) { "-h, -d, -u, -p and -i must be informed for database type" }
        }
    }
    private val host by option("-h", help = "host for database").default("")
    private val dbName by option("-d", help = "database name").default("")
    private val username by option("-u", help = "username for database").default("")
    private val password by option("-p", help = "password for database").default("")
    private val executionId by option("-i", help = "execution id to be read").int()
    private val teamsLocation by option("-t", help = "file to read the backends' config").file(
        mustExist = true, mustBeReadable = true, canBeDir = false
    )
    private val eventsLocation by option("-e", help = "file to read the events from").file(
        mustExist = true, mustBeReadable = true, canBeDir = false
    )
    private val failFast by option(
        "-f", help = "Stop the application if an action fails, otherwise ignore and continue processing next events"
    ).flag()
    @Suppress("MagicNumber")
    private val delayPool by option(
        "-d",
        "--delay-pool",
        help = "Time in milliseconds it will wait for a conversation to be added to the pool."
    ).long().default(1000L)
    private val logLevel by option("-l", "--log-level", help = "log level").enum<KaliumLogLevel>().default(KaliumLogLevel.INFO)
    private val logOutputFile by option("-o", "--log-file", help = "output file for logs")
    private val monkeysLogOutputFile by option("-m", "--monkeys-log-file", help = "output file for monkey logs")
    private val fileLogger: LogWriter by lazy { fileLogger(logOutputFile ?: "kalium.log") }
    private val monkeyFileLogger: LogWriter by lazy { fileLogger(monkeysLogOutputFile ?: "monkeys.log") }

    override fun run() = runBlocking(Dispatchers.Default) {
        if (logOutputFile != null) {
            CoreLogger.init(KaliumLogger.Config(logLevel, listOf(fileLogger)))
        } else {
            CoreLogger.init(KaliumLogger.Config(logLevel))
        }
        MonkeyLogger.init(KaliumLogger.Config(logLevel, listOf(monkeyFileLogger)))
        val eventStorage: EventStorage = when (eventLogType) {
            EventLogType.FILE -> FileStorage(FileStorageConfig(eventsLocation!!.absolutePath, teamsLocation!!.absolutePath))
            EventLogType.POSTGRES -> PostgresStorage(PostgresStorageConfig(host, dbName, username, password), executionId)
        }
        val backendConfigs = eventStorage.readTeamConfig()
        val users = TestDataImporter.generateUserData(backendConfigs)
        processEvents(users, eventStorage.readProcessedEvents(this))
    }

    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    @Suppress("TooGenericExceptionCaught")
    private suspend fun processEvents(users: List<UserData>, events: ReceiveChannel<Event>) {
        val logicClients = mutableMapOf<Int, CoreLogic>()
        val conversationPool = ConversationPool(delayPool)
        events.consumeEach { config ->
            val actionName = config.eventType::class.serializer().descriptor.serialName
            try {
                val monkeyPool = MonkeyPool(users, "Replayer", MonkeyConfig.Internal)
                val coreLogic = logicClients.getOrPut(config.monkeyOrigin.clientId) {
                    coreLogic("${homeDirectory()}/.kalium/replayer-${config.monkeyOrigin.clientId}")
                }
                logger.i("Running action $actionName")
                val startTime = System.currentTimeMillis()
                Action.eventFromConfig(config.monkeyOrigin, config.eventType).execute(
                    coreLogic,
                    monkeyPool,
                    conversationPool
                )
                logger.d("Action $actionName took ${System.currentTimeMillis() - startTime} milliseconds")
            } catch (e: Exception) {
                logger.e("Failed processing event: $actionName: $e", e)
                if (this.failFast) {
                    error("Failed processing event: $actionName: $e")
                }
            }
        }
    }
}
