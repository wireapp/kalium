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

@file:OptIn(kotlinx.cinterop.BetaInteropApi::class)
@file:Suppress("NoWildcardImports", "WildcardImport")

package com.wire.kalium.logic.util

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import platform.Foundation.NSData
import platform.Foundation.NSPipe
import platform.Foundation.NSString
import platform.Foundation.NSTask
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
// Wildcard needed: fileHandleForReading, readDataToEndOfFile, waitUntilExit are ObjC
// category extensions that cannot be imported individually in Kotlin/Native.
import platform.Foundation.*

internal data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

@OptIn(ExperimentalForeignApi::class)
internal fun execCommand(
    args: List<String>,
    workingDirectory: Path? = null,
): ProcessResult {
    require(args.isNotEmpty()) { "args must not be empty" }

    val task = NSTask()
    val stdoutPipe = NSPipe()
    val stderrPipe = NSPipe()

    task.executableURL = NSURL.fileURLWithPath(args.first())
    task.arguments = args.drop(1)
    task.standardOutput = stdoutPipe
    task.standardError = stderrPipe

    if (workingDirectory != null) {
        task.currentDirectoryURL = NSURL.fileURLWithPath(workingDirectory.toString())
    }

    task.launchAndReturnError(null)
    task.waitUntilExit()

    val stdoutData = stdoutPipe.fileHandleForReading.readDataToEndOfFile()
    val stderrData = stderrPipe.fileHandleForReading.readDataToEndOfFile()

    return ProcessResult(
        exitCode = task.terminationStatus,
        stdout = stdoutData.toUtf8String(),
        stderr = stderrData.toUtf8String(),
    )
}

private fun NSData.toUtf8String(): String =
    NSString.create(data = this, encoding = NSUTF8StringEncoding)?.toString() ?: ""
