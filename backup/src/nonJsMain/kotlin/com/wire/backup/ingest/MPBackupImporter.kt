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

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
public actual class MPBackupImporter : CommonMPBackupImporter() {

    /**
     * Imports a backup from the specified root path.
     *
     * @param multiplatformBackupFilePath the path to the decrypted, unzipped backup data file
     */
    @ObjCName("importFile")
    public fun importFromFile(multiplatformBackupFilePath: String): BackupImportResult {
        return FileSystem.SYSTEM.read(multiplatformBackupFilePath.toPath()) {
            importBackup(readByteArray())
        }
    }
}
