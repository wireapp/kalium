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
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.monkeys.conversation.RemoteMonkey
import com.wire.kalium.monkeys.model.Event
import com.wire.kalium.monkeys.model.EventType
import com.wire.kalium.monkeys.model.TestData
import com.wire.kalium.monkeys.model.TestDataImporter
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyConfig
import com.wire.kalium.monkeys.pool.MonkeyPool
import com.wire.kalium.monkeys.storage.DummyEventStorage
import com.wire.kalium.monkeys.storage.EventStorage
import com.wire.kalium.monkeys.storage.FileStorage
import com.wire.kalium.monkeys.storage.PostgresStorage
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stopServerOnCancellation
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import sun.misc.Signal
import sun.misc.SignalHandler
import java.io.File
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import java.util.jar.Manifest
import kotlin.coroutines.cancellation.CancellationException

fun CoroutineScope.stopIM() {
    logger.i("Stopping Infinite Monkeys")
    this.cancel("Stopping Infinite Monkeys")
    MonkeyApplication.isActive.set(false)
}

class MonkeyApplication : CliktCommand(allowMultipleSubcommands = true) {
    private val dataFilePath by argument(help = "path to the test data file")
    private val skipWarmup by option("-s", "--skip-warmup", help = "Should the warmup be skipped?").flag()
    private val sequentialWarmup by option("-w", "--sequential-warmup", help = "Should the warmup happen sequentially?").flag()
    private val logLevel by option("-l", "--log-level", help = "log level").enum<KaliumLogLevel>().default(KaliumLogLevel.INFO)
    private val logOutputFile by option("-f", "--log-file", help = "output file for logs")
    private val monkeysLogOutputFile by option("-m", "--monkeys-log-file", help = "output file for monkey logs")
    private val fileLogger: LogWriter by lazy { fileLogger(logOutputFile ?: "kalium.log") }
    private val monkeyFileLogger: LogWriter by lazy { fileLogger(monkeysLogOutputFile ?: "monkeys.log") }

    @Suppress("TooGenericExceptionCaught")
    override fun run() = try {
        runBlocking(Dispatchers.Default) {
            val signalHandler = SignalHandler { this.stopIM() }
            // stop on ctrl + c
            Signal.handle(Signal("INT"), signalHandler)
            // stop when parent process sends signal
            Signal.handle(Signal("HUP"), signalHandler)
            Signal.handle(Signal("TERM"), signalHandler)

            if (logOutputFile != null) {
                CoreLogger.init(KaliumLogger.Config(logLevel, listOf(fileLogger)))
            } else {
                CoreLogger.init(KaliumLogger.Config(logLevel))
            }
            MonkeyLogger.init(KaliumLogger.Config(logLevel, listOf(monkeyFileLogger)))

            logger.i("Initializing Metrics Endpoint")
            embeddedServer(Netty, port = 9090) {
                routing {
                    get("/") {
                        call.respondText(MetricsCollector.metrics())
                    }
                }
            }.start(false).stopServerOnCancellation()

            logger.i("Initializing Infinite Monkeys - CC: ${getCCVersion()}")
            val testData = TestDataImporter.importFromFile(dataFilePath)
            val eventProcessor = when (testData.eventStorage) {
                is com.wire.kalium.monkeys.model.EventStorage.FileStorage -> FileStorage(testData.eventStorage)
                is com.wire.kalium.monkeys.model.EventStorage.PostgresStorage -> PostgresStorage(testData.eventStorage)
                null -> DummyEventStorage()
            }
            eventProcessor.storeBackends(testData.backends)
            val kaliumCacheFolders = testData.testCases.map { it.name.replace(' ', '_') }
            try {
                runMonkeys(testData, eventProcessor)
            } catch (e: Throwable) {
                if (e !is CancellationException) {
                    logger.e("Error running Infinite Monkeys", e)
                }
            } finally {
                withContext(NonCancellable) {
                    if (testData.externalMonkey != null) {
                        logger.i("Shutting down remote monkeys")
                        RemoteMonkey.tearDown()
                    }
                    eventProcessor.releaseResources()
                    kaliumCacheFolders.forEach { File(it).deleteRecursively() }
                }
            }
        }
    } catch (e: CancellationException) {
        logger.i("Infinite Monkeys finished successfully")
    }

    private suspend fun runMonkeys(testData: TestData, eventStorage: EventStorage) {
        val users = TestDataImporter.generateUserData(testData.backends)
        testData.testCases.forEachIndexed { index, testCase ->
            val monkeyConfig = if (testData.externalMonkey != null) {
                logger.i("Starting ${users.size} external monkeys")
                MonkeyConfig.Remote(
                    testData.externalMonkey.startCommand,
                    testData.externalMonkey.addressTemplate::renderMonkeyTemplate,
                    Optional.ofNullable(testData.externalMonkey.waitForProcess)
                )
            } else {
                MonkeyConfig.Internal
            }
            val monkeyPool = MonkeyPool(users, testCase.name, monkeyConfig)
            monkeyPool.suspendInit()
            val coreLogic = coreLogic("$HOME_DIRECTORY/.kalium/${testCase.name.replace(' ', '_')}")
            // the first one creates the preset groups and logs everyone in so keypackages are created
            val eventChannel = Channel<Event>(Channel.UNLIMITED)
            if (index == 0) {
                if (!this.skipWarmup) {
                    logger.i("Creating initial key packages for clients (logging everyone in and out). This can take a while...")
                    monkeyPool.warmUp(coreLogic, sequentialWarmup)
                }
                logger.i("Creating prefixed groups")
                testData.conversationDistribution.forEach { (prefix, config) ->
                    ConversationPool.createPrefixedConversations(
                        coreLogic, prefix, config.groupCount, config.userCount, config.protocol, monkeyPool
                    ).forEach {
                        eventChannel.send(Event(it.owner, EventType.CreateConversation(it)))
                    }
                }
            }
            logger.i("Running setup for test case ${testCase.name}")
            runSetup(testCase.setup, coreLogic, monkeyPool, eventChannel)
            logger.i("Starting actions for test case ${testCase.name}")
            start(testCase.name, testCase.actions, coreLogic, monkeyPool, eventChannel)
            eventStorage.processEvents(eventChannel)
        }
    }

    companion object {
        val HOME_DIRECTORY: String = homeDirectory()
        val isActive = AtomicBoolean(true)

        fun getCCVersion(): String {
            this::class.java.classLoader?.getResources("META-INF/MANIFEST.MF")?.asIterator()?.forEach { url ->
                url.openStream().use {
                    val manifest = Manifest(it)
                    if (manifest.mainAttributes.getValue("CC-Version") != null)
                        return manifest.mainAttributes.getValue("CC-Version")
                }
            }
            return "Not-Found"
        }
    }
}
