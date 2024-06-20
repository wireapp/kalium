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

package com.wire.kalium.network.api.base.authenticated.prekey

import com.wire.kalium.network.api.authenticated.prekey.PreKeyDTO
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface PreKeyApi {
    /**
     * @param users a map of domain to (map of user IDs to client IDs)
     * @return a prekey for each one. You can't request information for more users than maximum conversation size.
     */
    suspend fun getUsersPreKey(
        users: Map<String, Map<String, List<String>>>
    ): NetworkResponse<ListPrekeysResponse>

    /**
     * Retrieves the IDs of the prekeys currently available in the backend
     * for the provided [clientId].
     * @see uploadNewPrekeys
     */
    suspend fun getClientAvailablePrekeys(clientId: String): NetworkResponse<List<Int>>

    /**
     * Uploads more prekeys to be associated with the provided [clientId],
     * which can be used by other users to start conversations with the client.
     * @see getClientAvailablePrekeys
     */
    suspend fun uploadNewPrekeys(
        clientId: String,
        preKeys: List<PreKeyDTO>
    ): NetworkResponse<Unit>
}

typealias DomainToUserIdToClientsToPreKeyMap = Map<String, Map<String, Map<String, PreKeyDTO?>>>
typealias DomainToUserIdToClientsMap = Map<String, Map<String, List<String>>>

/**
 * v4 API response type for prekeys
 * Will extend to older versions of the API, to support backwards compatibility plus versioning
 */
@Serializable
data class ListPrekeysResponse(
    @SerialName("failed_to_list")
    val failedToList: List<UserId>? = listOf(),
    @SerialName("qualified_user_client_prekeys")
    val qualifiedUserClientPrekeys: DomainToUserIdToClientsToPreKeyMap
)
