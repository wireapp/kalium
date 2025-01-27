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
internal class InMemoryEntryStorage : EntryStorage {

    private val entries = mutableMapOf<String, ByteArray>()

    override fun persistEntry(backupEntry: BackupEntry) {
        check(!entries.containsKey(backupEntry.name)) { "Entry with name ${backupEntry.name} already exists." }
        entries[backupEntry.name] = backupEntry.data.buffer().readByteArray()
    }

    override operator fun get(entryName: String): BackupEntry? = entries[entryName]?.let {
        val buffer = Buffer().apply { write(it) }
        BackupEntry(entryName, buffer)
    }

    override fun listEntries(): List<BackupEntry> = entries.map { data ->
        val buffer = Buffer().apply { write(data.value) }
        BackupEntry(data.key, buffer)
    }

    override fun clear() {
        entries.clear()
    }
}
