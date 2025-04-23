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
package com.wire.backup.dump

import org.khronos.webgl.Uint8Array

@JsExport
public sealed class BackupExportResult {
    /**
     * Represents a successful export.
     *
     * @property bytes the exported data.
     * @property fileName a suggested file name for the backup file, containing the type.
     *
     * @see Failure for more information about failures.
     *
     */
    public class Success(
        public val bytes: Uint8Array,
        public val fileName: String,
    ) : BackupExportResult()
    public sealed class Failure(public val message: String) : BackupExportResult() {
        /**
         * Represents an I/O error that occurs during an export process.
         *
         * It's unlikely for this to ever be thrown on JavaScript/Browser
         */
        public class IOError(message: String) : Failure(message)

        /**
         * An error happened during the zipping process.
         */
        public class ZipError(message: String) : Failure(message)
    }
}
