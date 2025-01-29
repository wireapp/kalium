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
public sealed class BackupPeekResult {
    /**
     * The provided data corresponds to a compatible backup artifact.
     *
     * @property isCreatedBySameUser - true if the provided UserId matches the UserId that created the backup.
     */
    public data class Success(
        val version: String,
        val isEncrypted: Boolean,
        val isCreatedBySameUser: Boolean,
        /** TODO: Add more info about the backup */
    ) : BackupPeekResult()

    public sealed class Failure : BackupPeekResult() {
        public data object UnknownFormat : Failure()
        public data class UnsupportedVersion(val backupVersion: String) : Failure()
    }
}
