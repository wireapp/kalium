/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.nse.feasibility

import kotlinx.coroutines.runBlocking
import platform.posix.fflush
import platform.posix.getpid
import platform.posix.sleep
import platform.posix.stdout
import kotlin.system.exitProcess

public fun main(args: Array<String>): Unit = runProbeCommand(args)

internal fun runProbeCommand(args: Array<String>) {
    if (args.size < REQUIRED_ARGUMENT_COUNT) usage()

    val command = args[0]
    val sharedRoot = args[1]
    val probe = NseFeasibilityProbe()

    when (command) {
        "path" -> printResult(probe.probeSharedRoot(sharedRoot))
        "lock-try" -> {
            val attempt = probe.tryAcquireProcessLock(sharedRoot)
            println(
                "gate=process-lock acquired=${attempt.acquired} elapsedNanos=${attempt.elapsedNanos} " +
                        "detail=${attempt.detail}"
            )
            attempt.lock?.release()
            if (!attempt.acquired) exitProcess(LOCK_UNAVAILABLE_EXIT_CODE)
        }

        "lock-hold" -> {
            val seconds = args.getOrNull(2)?.toUIntOrNull() ?: DEFAULT_HOLD_SECONDS
            val attempt = probe.tryAcquireProcessLock(sharedRoot)
            if (!attempt.acquired) {
                println("gate=process-lock acquired=false detail=${attempt.detail}")
                exitProcess(LOCK_UNAVAILABLE_EXIT_CODE)
            }
            println("gate=process-lock acquired=true pid=${getpid()} path=${attempt.lock?.path}")
            fflush(stdout)
            sleep(seconds)
            attempt.lock?.release()
        }

        "corecrypto" -> printResult(runBlocking { probe.probeSequentialCoreCryptoOpenClose(sharedRoot) })
        "content" -> printResult(probe.probeMessageContentExtraction())
        "network" -> printResult(probe.probeNotificationWebSocketLinkage())
        "persistence" -> printResult(probe.inspectCurrentApplePersistenceSurface(sharedRoot, "nse-feasibility"))
        else -> usage()
    }
}

private fun printResult(result: FeasibilityProbeResult) {
    println(
        "gate=${result.gate} passed=${result.passed} elapsedNanos=${result.elapsedNanos} " +
                "detail=${result.detail}"
    )
    if (!result.passed && result.gate != "apple-persistence-security") exitProcess(PROBE_FAILED_EXIT_CODE)
}

private fun usage(): Nothing {
    println(
        "usage: kalium-nse-feasibility " +
                "<path|lock-try|lock-hold|corecrypto|content|network|persistence> <absolute-shared-root> [hold-seconds]"
    )
    exitProcess(USAGE_EXIT_CODE)
}

private const val REQUIRED_ARGUMENT_COUNT = 2
private const val DEFAULT_HOLD_SECONDS = 30u
private const val USAGE_EXIT_CODE = 64
private const val LOCK_UNAVAILABLE_EXIT_CODE = 75
private const val PROBE_FAILED_EXIT_CODE = 1
