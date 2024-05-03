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

package com.wire.kalium.network.api.base.authenticated.search

import com.wire.kalium.network.api.base.model.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserSearchResponse(
    @SerialName("documents") val documents: List<ContactDTO>,
    @SerialName("found") val found: Int,
    @SerialName("returned") val returned: Int,
    @SerialName("search_policy") val searchPolicy: SearchPolicyDTO,
    @SerialName("took") val took: Int
)

@Serializable
data class ContactDTO(
    @SerialName("accent_id") val accentId: Int?,
    @SerialName("handle") val handle: String?,
    @SerialName("name") val name: String,
    @SerialName("qualified_id") val qualifiedID: UserId,
    @SerialName("team") val team: String?
)

@Serializable
enum class SearchPolicyDTO {
    @SerialName("no_search")
    NO_SEARCH,

    @SerialName("exact_handle_search")
    EXACT_HANDLE_SEARCH,

    @SerialName("full_search")
    FULL_SEARCH;

    override fun toString(): String {
        return this.name.lowercase()
    }
}
