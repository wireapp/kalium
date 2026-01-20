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

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.api.authenticated.remoteBackup.DeleteMessagesResponseDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.MessageSyncFetchResponseDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.MessageSyncRequestDTO
import com.wire.kalium.network.api.base.authenticated.RemoteBackupApi
import com.wire.kalium.network.utils.NetworkResponse
import okio.Sink
import okio.Source

internal open class RemoteBackupApiV0 internal constructor() : RemoteBackupApi {
    override suspend fun syncMessages(request: MessageSyncRequestDTO): NetworkResponse<Unit> =
        RemoteBackupApi.getApiNotSupportError("syncMessages")

    override suspend fun fetchMessages(
        user: String,
        since: Long?,
        conversation: String?,
        paginationToken: String?,
        size: Int
    ): NetworkResponse<MessageSyncFetchResponseDTO> =
        RemoteBackupApi.getApiNotSupportError("fetchMessages")

    override suspend fun deleteMessages(
        userId: String?,
        conversationId: String?,
        before: Long?
    ): NetworkResponse<DeleteMessagesResponseDTO> =
        RemoteBackupApi.getApiNotSupportError("deleteMessages")

    override suspend fun uploadStateBackup(
        userId: String,
        backupDataSource: () -> Source,
        backupSize: Long
    ): NetworkResponse<Unit> =
        RemoteBackupApi.getApiNotSupportError("uploadStateBackup")

    override suspend fun downloadStateBackup(
        userId: String,
        tempFileSink: Sink
    ): NetworkResponse<Unit> =
        RemoteBackupApi.getApiNotSupportError("downloadStateBackup")
}
