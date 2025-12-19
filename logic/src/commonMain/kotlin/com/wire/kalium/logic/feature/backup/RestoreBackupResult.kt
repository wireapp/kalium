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
package com.wire.kalium.logic.feature.backup

public sealed class RestoreBackupResult {
    public data class Failure(val failure: BackupRestoreFailure) : RestoreBackupResult()
    public data object Success : RestoreBackupResult()

    public sealed class BackupRestoreFailure(public open val cause: String) {
        public data object InvalidPassword : BackupRestoreFailure("The provided password is invalid")
        public data object InvalidUserId : BackupRestoreFailure("User id in the backup file does not match the current user id")
        public data class IncompatibleBackup(override val cause: String) : BackupRestoreFailure(cause)
        public data class BackupIOFailure(override val cause: String) : BackupRestoreFailure(cause)
        public data class DecryptionFailure(override val cause: String) : BackupRestoreFailure(cause)
    }
}
