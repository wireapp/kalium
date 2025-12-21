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

package com.wire.kalium.network.api.base.authenticated.backup

import com.wire.kalium.network.api.model.DeleteMessagesResponseDTO
import com.wire.kalium.network.api.model.MessageSyncFetchResponseDTO
import com.wire.kalium.network.api.model.MessageSyncRequestDTO
import com.wire.kalium.network.api.model.StateBackupUploadResponse
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mockable
import okio.Source

/**
 * API client for synchronizing messages to an external backup service
 */
@Mockable
interface MessageSyncApi {
    /**
     * Synchronizes message updates to the backup service
     * @param request The sync request containing user ID and message updates
     * @return Network response indicating success or failure
     */
    suspend fun syncMessages(request: MessageSyncRequestDTO): NetworkResponse<Unit>

    /**
     * Fetches messages from the backup service with filtering and pagination
     * @param userId User ID to fetch messages for
     * @param since Timestamp in epoch milliseconds for cursor-based pagination (optional)
     * @param conversationId Filter by conversation ID (optional)
     * @param order Sort order: "asc" or "desc" (default: "asc")
     * @param size Page size: 1-1000 (default: 100)
     * @return Network response containing paginated messages
     */
    suspend fun fetchMessages(
        userId: String,
        since: Long? = null,
        conversationId: String? = null,
        order: String = "asc",
        size: Int = 100
    ): NetworkResponse<MessageSyncFetchResponseDTO>

    /**
     * Deletes messages from the backup service based on filter criteria
     * At least one filter parameter must be provided
     * @param userId Delete messages for this user (optional)
     * @param conversationId Delete messages in this conversation (optional)
     * @param before Delete messages before this timestamp in epoch milliseconds (optional)
     * @return Network response containing the count of deleted messages
     */
    suspend fun deleteMessages(
        userId: String? = null,
        conversationId: String? = null,
        before: Long? = null
    ): NetworkResponse<DeleteMessagesResponseDTO>

    /**
     * Uploads the cryptographic state backup for the specified user
     * @param userId User ID to backup state for
     * @param backupDataSource Lazy source providing the zip file data
     * @param backupSize Size of the backup data in bytes
     * @return Network response indicating upload success (empty response body)
     */
    suspend fun uploadStateBackup(
        userId: String,
        backupDataSource: () -> Source,
        backupSize: Long
    ): NetworkResponse<Unit>

    companion object {
        fun getApiNotSupportError(apiName: String, apiVersion: String = "12") = NetworkResponse.Error(
            APINotSupported("${this::class.simpleName}: $apiName api is only available on API V$apiVersion")
        )
    }
}
