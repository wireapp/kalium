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
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.monkeys.importer.TestData
import com.wire.kalium.monkeys.importer.TestDataImporter
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyPool
import io.ktor.server.application.call
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runBlocking
import sun.misc.Signal

class MonkeyApplication : CliktCommand(allowMultipleSubcommands = true) {

    private val dataFilePath by argument(help = "path to the test data file")
    private val logLevel by option("-l", "--log-level", help = "log level").enum<KaliumLogLevel>().default(KaliumLogLevel.INFO)
    private val logOutputFile by option("-f", "--log-file", help = "output file for logs")
    private val monkeysLogOutputFile by option("-m", "--monkeys-log-file", help = "output file for monkey logs")
    private val fileLogger: LogWriter by lazy { fileLogger(logOutputFile ?: "kalium.log") }
    private val monkeyFileLogger: LogWriter by lazy { fileLogger(monkeysLogOutputFile ?: "monkeys.log") }

    override fun run() = runBlocking(Dispatchers.Default) {
        // stop on ctrl + c
        Signal.handle(Signal(("INT"))) {
            logger.i("Stopping Infinite Monkeys")
            this.coroutineContext.cancelChildren()
        }

        if (logOutputFile != null) {
            CoreLogger.init(KaliumLogger.Config(logLevel, listOf(fileLogger)))
        } else {
            CoreLogger.init(KaliumLogger.Config(logLevel))
        }
        MonkeyLogger.init(KaliumLogger.Config(logLevel, listOf(monkeyFileLogger)))
        logger.i("Initializing Metrics Endpoint")
        io.ktor.server.engine.embeddedServer(Netty, port = 9090) {
            routing {
                get("/") {
                    call.respondText(MetricsCollector.metrics())
                }
            }
        }.start(false)

        logger.i("Initializing Infinite Monkeys")
        val testData = TestDataImporter.importFromFile(dataFilePath)
        runMonkeys(testData)
    }

    private suspend fun runMonkeys(
        testData: TestData
    ) {
        val users = TestDataImporter.generateUserData(testData)
        return testData.testCases.forEachIndexed { index, testCase ->
            val coreLogic = coreLogic("$HOME_DIRECTORY/.kalium/${testCase.name.replace(' ', '_')}")
            logger.i("Logging in and out all users to create key packages")
            val monkeyPool = MonkeyPool(users, testCase.name)
            // the first one creates the preset groups and logs everyone in so keypackages are created
            if (index == 0) {
                logger.i("Creating initial key packages for clients (logging everyone in and out). This can take a while...")
                monkeyPool.warmUp(coreLogic)
                logger.i("Creating prefixed groups")
                testData.conversationDistribution.forEach { (prefix, config) ->
                    ConversationPool.createPrefixedConversations(
                        coreLogic, prefix, config.groupCount, config.userCount, config.protocol, monkeyPool
                    )
                }
            }
            logger.i("Running setup for test case ${testCase.name}")
            ActionScheduler.runSetup(testCase.setup, coreLogic, monkeyPool)
            logger.i("Starting actions for test case ${testCase.name}")
            ActionScheduler.start(testCase.name, testCase.actions, coreLogic, monkeyPool)
        }
    }

    companion object {
        val HOME_DIRECTORY: String = homeDirectory()
    }
}
