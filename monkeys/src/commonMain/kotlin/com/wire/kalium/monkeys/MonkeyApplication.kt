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

@file:Suppress("TooManyFunctions")

package com.wire.kalium.monkeys

import co.touchlab.kermit.LogWriter
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.monkeys.importer.TestDataImporter
import kotlinx.coroutines.runBlocking

class MonkeyApplication : CliktCommand(allowMultipleSubcommands = true) {

    private val dataFilePath by argument(help = "path to the test data file")
    private val logLevel by option(help = "log level").enum<KaliumLogLevel>().default(KaliumLogLevel.VERBOSE)
    private val logOutputFile by option(help = "output file for logs")
    private val fileLogger: LogWriter by lazy { fileLogger(logOutputFile ?: "kalium.log") }
    private val proteus by option(help = "run using proteus protocol").flag()

    override fun run() = runBlocking {
        val coreLogic = coreLogic(
            rootPath = "$HOME_DIRECTORY/.kalium/accounts",
            kaliumConfigs = KaliumConfigs(
                developmentApiEnabled = true,
                encryptProteusStorage = true,
                isMLSSupportEnabled = true,
            )
        )

        if (logOutputFile != null) {
            CoreLogger.setLoggingLevel(logLevel, fileLogger)
        } else {
            CoreLogger.setLoggingLevel(logLevel)
        }

        coreLogic.updateApiVersionsScheduler.scheduleImmediateApiVersionUpdate()
        val users = TestDataImporter().importFromFile(dataFilePath)
        runMonkeys(coreLogic, users, SimpleTestSequence())
    }

    private suspend fun runMonkeys(
        coreLogic: CoreLogic,
        users: List<UserData>,
        testSequence: TestSequence
    ) = with(testSequence) {
        val protocol = if (proteus) ConversationOptions.Protocol.PROTEUS else ConversationOptions.Protocol.MLS
        val monkeyGroups = split(users)
        val monkeyScopes = setup(coreLogic, monkeyGroups)
        val conversations = createConversations(monkeyScopes, protocol)
        commands.forEach { command ->
            command(conversations)
        }
    }

    companion object {
        val HOME_DIRECTORY: String = homeDirectory()
    }

}

expect fun fileLogger(filePath: String): LogWriter

expect fun homeDirectory(): String

expect fun coreLogic(rootPath: String, kaliumConfigs: KaliumConfigs): CoreLogic
