/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.network.api.authenticated.remoteBackup

/**
 * Events exchanged with the remote backup service.
 */
sealed class RemoteBackupEventDTO {

    data class Upsert(
        val messageId: String,
        val timestamp: Long,
        val payload: RemoteBackupPayloadDTO
    ) : RemoteBackupEventDTO()

    data class Delete(
        val conversationId: String,
        val messageId: String
    ) : RemoteBackupEventDTO()

    data class LastRead(
        val conversationId: String,
        val lastRead: Long
    ) : RemoteBackupEventDTO()
}
