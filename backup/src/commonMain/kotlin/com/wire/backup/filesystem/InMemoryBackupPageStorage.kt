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

import okio.Buffer
import okio.buffer

/**
 * As JS on a browser doesn't have access to files, we just write stuff in memory.
 * It is also useful for tests.
 */
internal class InMemoryBackupPageStorage : BackupPageStorage {

    private val entries = mutableMapOf<String, ByteArray>()

    override fun persistEntry(backupPage: BackupPage) {
        check(!entries.containsKey(backupPage.name)) { "Entry with name ${backupPage.name} already exists." }
        entries[backupPage.name] = backupPage.data.buffer().readByteArray()
    }

    override operator fun get(entryName: String): BackupPage? = entries[entryName]?.let {
        val buffer = Buffer().apply { write(it) }
        BackupPage(entryName, buffer)
    }

    override fun listEntries(): List<BackupPage> = entries.map { data ->
        val buffer = Buffer().apply { write(data.value) }
        BackupPage(data.key, buffer)
    }

    override fun clear() {
        entries.clear()
    }
}
