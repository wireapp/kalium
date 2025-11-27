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

import kotlin.js.JsExport

@JsExport
public sealed class BackupImportResult {
    public class Success(public val pager: ImportResultPager) : BackupImportResult()
    public sealed class Failure : BackupImportResult() {
        /**
         * The file has an incompatible format.
         * _i.e._ it isn't a Wire Backup file, or it is from an unsupported version.
         */
        public data object ParsingFailure : Failure()
        public data object MissingOrWrongPassphrase : Failure()

        /**
         * Error thrown during unzipping.
         */
        public data class UnzippingError(public val message: String) : Failure()
        public data class UnknownError(public val message: String) : Failure()
    }
}
