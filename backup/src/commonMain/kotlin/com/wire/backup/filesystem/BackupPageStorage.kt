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
package com.wire.backup.filesystem

import okio.Source
import okio.use

/**
 * Storage used during export/import of backups.
 */
internal interface BackupPageStorage {
    fun persistEntry(backupPage: BackupPage)
    operator fun get(entryName: String): BackupPage?
    fun listEntries(): List<BackupPage>
    fun clear()
}

internal data class BackupPage(
    val name: String,
    private val data: Source,
) {

    fun close() {
        data.close()
    }

    fun <T> use(useFunction: (Source) -> T): T = data.use {
        useFunction(it)
    }

    internal companion object {
        const val CONVERSATIONS_PREFIX = "conversations_"
        const val MESSAGES_PREFIX = "messages_"
        const val USERS_PREFIX = "users_"
        const val PAGE_SUFFIX = ".binpb"
    }
}
