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

import com.wire.kalium.network.api.model.MessageSyncFetchResponseDTO
import com.wire.kalium.network.api.model.MessageSyncRequestDTO
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mockable

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

    companion object {
        fun getApiNotSupportError(apiName: String, apiVersion: String = "12") = NetworkResponse.Error(
            APINotSupported("${this::class.simpleName}: $apiName api is only available on API V$apiVersion")
        )
    }
}
