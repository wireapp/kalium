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

import com.wire.backup.dump.JSZip
import com.wire.backup.filesystem.BackupEntry
import com.wire.backup.filesystem.EntryStorage
import com.wire.backup.filesystem.InMemoryEntryStorage
import ext.libsodium.com.ionspin.kotlin.crypto.toUByteArray
import ext.libsodium.com.ionspin.kotlin.crypto.toUInt8Array
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import okio.Buffer
import okio.Sink
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

@JsExport
public actual class MPBackupImporter : CommonMPBackupImporter() {
    private val inMemoryUnencryptedBuffer = Buffer()

    public fun importFromFileData(data: ByteArray, passphrase: String?): Promise<BackupImportResult> = GlobalScope.promise {
        val buffer = Buffer()
        buffer.write(data)
        importBackup(buffer, passphrase)
    }

    override fun getUnencryptedArchiveSink(): Sink = inMemoryUnencryptedBuffer

    override suspend fun unzipAllEntries(): EntryStorage {
        // TODO: Improve performance and save memory by avoiding array conversions
        val zip = JSZip.loadAsync(inMemoryUnencryptedBuffer.readByteArray().toUByteArray().toUInt8Array()).await()
        val storage = InMemoryEntryStorage()
        val entryNames = keys(zip.files)
        for (entry in entryNames) {
            val promise = zip.files[entry].async("uint8array")
            val data = promise.unsafeCast<Promise<Uint8Array>>().await()
            val buffer = Buffer()
            buffer.write(data.toUByteArray().toByteArray())
            storage.persistEntry(BackupEntry(entry, buffer))
        }
        return storage
    }

    private fun keys(json: dynamic) = js("Object").keys(json).unsafeCast<Array<String>>()
}
