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

package com.wire.kalium.util

import kotlinx.coroutines.await
import kotlin.js.Promise

actual object FileUtil {
    actual fun mkDirs(path: String): Boolean {
        // Web targets do not expose a host filesystem here; crypto persistence is handled elsewhere.
        return path.isNotBlank()
    }

    actual fun deleteDirectory(path: String): Boolean {
        return path.isNotBlank()
    }

    actual suspend fun deletePersistentDirectory(path: String): Boolean {
        val indexedDb = js("window.indexedDB")
        return when {
            path.isBlank() -> false
            indexedDb == null -> false
            js("typeof indexedDb.databases !== 'function'") as Boolean -> deleteIndexedDb(indexedDb, path).await()
            else -> {
                val databases = indexedDb.databases().unsafeCast<Promise<Array<dynamic>>>().await()
                val matchingNames = databases
                    .mapNotNull { it?.name as? String }
                    .filter { name -> name == path || name.startsWith("$path/") }

                matchingNames.isEmpty() || matchingNames.map { deleteIndexedDb(indexedDb, it).await() }.all { it }
            }
        }
    }

    actual fun isDirectoryNonEmpty(path: String): Boolean {
        return false
    }
}

private fun deleteIndexedDb(indexedDb: dynamic, name: String): Promise<Boolean> = Promise { resolve, _ ->
    val request = indexedDb.deleteDatabase(name)
    request.onsuccess = {
        resolve(true)
    }
    request.onerror = {
        resolve(false)
    }
    request.onblocked = {
        resolve(false)
    }
}
