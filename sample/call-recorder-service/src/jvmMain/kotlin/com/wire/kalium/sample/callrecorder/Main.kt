@file:OptIn(com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi::class)
@file:Suppress("TooGenericExceptionCaught")

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.wire.kalium.sample.callrecorder

import com.wire.kalium.logic.service.api.ServiceResult
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking

internal fun main(args: Array<String>) {
    val exitCode = runBlocking { runApplication(args) }
    if (exitCode != SUCCESS_EXIT_CODE) exitProcess(exitCode)
}

@Suppress("ReturnCount")
private suspend fun runApplication(args: Array<String>): Int {
    val options = try {
        parseOptions(args)
    } catch (failure: IllegalArgumentException) {
        System.err.println("ERROR ${failure.message}")
        printUsage()
        return CONFIGURATION_ERROR_EXIT_CODE
    }
    if (options == null) return SUCCESS_EXIT_CODE

    val loaded = try {
        ConfigurationLoader.load(options)
    } catch (failure: Throwable) {
        SafeLog.error(failure.message ?: "Unable to load configuration", failure)
        return CONFIGURATION_ERROR_EXIT_CODE
    }
    val service = try {
        CallRecorderService.create(loaded.wire, options.recordingsDirectory)
    } finally {
        loaded.clearInputSecrets()
    }
    val shutdownHook = Thread(
        {
            runBlocking { service.close() }
        },
        "call-recorder-shutdown",
    )
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    return try {
        when (service.start()) {
            ServiceResult.Success -> {
                service.awaitTermination()
                SUCCESS_EXIT_CODE
            }
            is ServiceResult.Failure -> STARTUP_ERROR_EXIT_CODE
        }
    } finally {
        service.close()
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
        loaded.close()
    }
}

private fun parseOptions(args: Array<String>): ApplicationOptions? {
    var configFile = Path.of("./call-recorder-config.json")
    var recordingsDirectory = Path.of("./recordings")
    var shutdownTimeoutSeconds = DEFAULT_SHUTDOWN_TIMEOUT_SECONDS
    var notificationTimeoutSeconds = DEFAULT_NOTIFICATION_TIMEOUT_SECONDS
    var index = 0
    while (index < args.size) {
        when (val option = args[index]) {
            "--help", "-h" -> {
                printUsage()
                return null
            }
            "--config" -> configFile = Path.of(args.requireValue(++index, option))
            "--recordings-dir" -> recordingsDirectory = Path.of(args.requireValue(++index, option))
            "--shutdown-timeout-seconds" -> shutdownTimeoutSeconds = args.positiveLong(++index, option)
            "--notification-open-timeout-seconds" -> notificationTimeoutSeconds = args.positiveLong(++index, option)
            else -> throw IllegalArgumentException("Unknown option: $option")
        }
        index++
    }
    return ApplicationOptions(
        configFile = configFile,
        recordingsDirectory = recordingsDirectory,
        shutdownTimeoutMillis = shutdownTimeoutSeconds * MILLIS_PER_SECOND,
        notificationOpenTimeoutMillis = notificationTimeoutSeconds * MILLIS_PER_SECOND,
    )
}

private fun Array<String>.requireValue(index: Int, option: String): String =
    getOrNull(index)?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("$option requires a value")

private fun Array<String>.positiveLong(index: Int, option: String): Long =
    requireValue(index, option).toLongOrNull()?.takeIf { it > 0 }
        ?: throw IllegalArgumentException("$option requires a positive integer")

private fun printUsage() {
    println(
        """
        Usage: ./gradlew :sample:call-recorder-service:jvmRun --args="[options]"

        Options:
          --config PATH                         Test JSON config (default: ./call-recorder-config.json)
          --recordings-dir PATH                 WAV output directory (default: ./recordings)
          --shutdown-timeout-seconds SECONDS    Graceful runtime shutdown timeout (default: 30)
          --notification-open-timeout-seconds S WebSocket readiness timeout (default: 30)
          --help                                Show this help

        This test sample reads all account, backend, client, and local database configuration
        from the JSON file. See sample/call-recorder-service/README.md.
        """.trimIndent(),
    )
}

private const val DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 30L
private const val DEFAULT_NOTIFICATION_TIMEOUT_SECONDS = 30L
private const val MILLIS_PER_SECOND = 1_000L
private const val SUCCESS_EXIT_CODE = 0
private const val CONFIGURATION_ERROR_EXIT_CODE = 2
private const val STARTUP_ERROR_EXIT_CODE = 3
