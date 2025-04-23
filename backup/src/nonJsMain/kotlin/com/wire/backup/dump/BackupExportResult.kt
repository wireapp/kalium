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

public sealed interface BackupExportResult {
    /**
     * Represents a successful result of a backup export operation.
     *
     * @property pathToOutputFile The path to the resulting output file of the export.
     */
    public class Success(public val pathToOutputFile: String) : BackupExportResult
    public sealed interface Failure : BackupExportResult {
        public val message: String

        /**
         * Represents an I/O error that occurs during an export process.
         */
        public class IOError(override val message: String) : Failure

        /**
         * An error happened during the zipping process.
         */
        public class ZipError(override val message: String) : Failure
    }
}
