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
package com.wire.backup.dump

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.BackupUser
import com.wire.backup.filesystem.BackupPage
import com.wire.backup.filesystem.BackupPageStorage
import com.wire.backup.filesystem.InMemoryBackupPageStorage
import com.wire.kalium.protobuf.backup.BackupData
import ext.libsodium.com.ionspin.kotlin.crypto.toUByteArray
import ext.libsodium.com.ionspin.kotlin.crypto.toUInt8Array
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asDeferred
import kotlinx.coroutines.promise
import okio.Buffer
import okio.Source
import okio.buffer
import kotlin.js.Promise

/**
 * Entity able to serialize [BackupData] entities, like [BackupMessage], [BackupConversation], [BackupUser]
 * into a cross-platform [BackupData] format.
 * @sample samples.backup.BackupSamplesJs.exportBackup
 */
@JsExport
public actual class MPBackupExporter(
    selfUserId: BackupQualifiedId
) : CommonMPBackupExporter(selfUserId) {

    override val storage: BackupPageStorage = InMemoryBackupPageStorage()

    // This shouldn't be used by clients anyway, so it's fine if we can't sport it to JS!
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun zipEntries(data: List<BackupPage>): Deferred<Source> {
        val zip = JSZip()
        data.forEach { entry ->
            // TODO: Save memory and improve performance by avoid array duplication!
            zip.file(entry.name, entry.data.buffer().readByteArray().toUByteArray().toUInt8Array())
        }
        val result = zip.generateAsync(ZipOptions()).then {
            val buffer = Buffer()
            // TODO: Save memory and improve performance by avoid array duplication!
            val entryData = it.toUByteArray().toByteArray()
            buffer.write(entryData)
            buffer.flush()
            return@then buffer
        }
        return result.asDeferred()
    }

    public fun finalize(password: String?): Promise<BackupExportResult> = GlobalScope.promise {
        val output = Buffer()
        when (val result = finalize(password, output)) {
            is ExportResult.Failure.IOError -> BackupExportResult.Failure.IOError(result.message)
            is ExportResult.Failure.ZipError -> BackupExportResult.Failure.ZipError(result.message)
            ExportResult.Success -> BackupExportResult.Success(output.readByteArray())
        }
    }
}
