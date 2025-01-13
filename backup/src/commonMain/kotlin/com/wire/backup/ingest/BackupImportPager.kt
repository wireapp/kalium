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

import com.wire.backup.data.BackupData
import com.wire.backup.filesystem.EntryStorage
import okio.buffer
import pbandk.decodeFromByteArray
import kotlin.js.JsExport
import com.wire.kalium.protobuf.backup.BackupData as ProtoBackupData

@JsExport
public class BackupImportPager internal constructor(private val storage: EntryStorage) {
    private val mapper = MPBackupMapper()
    private var currentPageIndex = 0
    private val entries = storage.listEntries().toMutableList()

    public fun hasMorePages(): Boolean = currentPageIndex < entries.size

    public fun nextPage(): BackupData? {
        val page = entries.removeFirstOrNull() ?: return null
        val bytes = page.data.buffer().readByteArray()
        return mapper.fromProtoToBackupModel(ProtoBackupData.decodeFromByteArray(bytes))
    }
}
