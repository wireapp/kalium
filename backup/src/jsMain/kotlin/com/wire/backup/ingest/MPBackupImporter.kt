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
package com.wire.backup.ingest

import com.wire.backup.filesystem.EntryStorage
import com.wire.backup.filesystem.InMemoryEntryStorage
import okio.Buffer
import okio.Sink

@JsExport
public actual class MPBackupImporter : CommonMPBackupImporter() {
    private val inMemoryUnencryptedBuffer = Buffer()

    override fun getUnencryptedArchiveSink(): Sink = inMemoryUnencryptedBuffer

    override fun unzipAllEntries(): EntryStorage {
        TODO("Unzip the whole in memory buffer, and return an InMemoryStorage with the unzipped entries")
        return InMemoryEntryStorage()
    }
}
