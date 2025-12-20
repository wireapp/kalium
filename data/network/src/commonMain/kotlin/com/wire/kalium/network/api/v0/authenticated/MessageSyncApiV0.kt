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

import com.wire.kalium.network.api.base.authenticated.backup.MessageSyncApi
import com.wire.kalium.network.api.model.DeleteMessagesResponseDTO
import com.wire.kalium.network.api.model.MessageSyncFetchResponseDTO
import com.wire.kalium.network.api.model.MessageSyncRequestDTO
import com.wire.kalium.network.api.model.StateBackupUploadResponse
import com.wire.kalium.network.utils.NetworkResponse
import okio.Source

internal open class MessageSyncApiV0 internal constructor() : MessageSyncApi {
    override suspend fun syncMessages(request: MessageSyncRequestDTO): NetworkResponse<Unit> =
        MessageSyncApi.getApiNotSupportError(::syncMessages.name)

    override suspend fun fetchMessages(
        userId: String,
        since: Long?,
        conversationId: String?,
        order: String,
        size: Int
    ): NetworkResponse<MessageSyncFetchResponseDTO> =
        MessageSyncApi.getApiNotSupportError(::fetchMessages.name)

    override suspend fun deleteMessages(
        userId: String?,
        conversationId: String?,
        before: Long?
    ): NetworkResponse<DeleteMessagesResponseDTO> =
        MessageSyncApi.getApiNotSupportError(::deleteMessages.name)

    override suspend fun uploadStateBackup(
        userId: String,
        backupDataSource: () -> Source,
        backupSize: Long
    ): NetworkResponse<StateBackupUploadResponse> =
        MessageSyncApi.getApiNotSupportError(::uploadStateBackup.name)
}
