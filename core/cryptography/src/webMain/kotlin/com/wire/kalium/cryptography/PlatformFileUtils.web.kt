/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.cryptography

// CoreCrypto web persists its own state in IndexedDB. These filesystem helpers are only
// relevant for native file-based storage/migration paths and should stay inert on JS.
internal actual fun createDirectory(path: String): Boolean {
    logJsFilesystemShimUsageOnce("createDirectory", path)
    return true
}

internal actual fun fileExists(path: String): Boolean {
    logJsFilesystemShimUsageOnce("fileExists", path)
    return false
}

internal actual fun deleteFile(path: String): Boolean {
    logJsFilesystemShimUsageOnce("deleteFile", path)
    return false
}

private var hasLoggedJsFilesystemShimUsage = false

private fun logJsFilesystemShimUsageOnce(operation: String, path: String) {
    if (hasLoggedJsFilesystemShimUsage) return

    hasLoggedJsFilesystemShimUsage = true
    kaliumLogger.w(
        "JS filesystem shim invoked via '$operation' for path '$path'. " +
            "CoreCrypto web uses IndexedDB-backed persistence, so file-based helpers stay inert on JS."
    )
}
