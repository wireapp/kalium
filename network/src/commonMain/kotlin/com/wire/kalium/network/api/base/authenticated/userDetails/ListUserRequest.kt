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

package com.wire.kalium.network.api.base.authenticated.userDetails

import com.wire.kalium.network.api.base.model.QualifiedHandle
import com.wire.kalium.network.api.base.model.QualifiedID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class ListUserRequest {
    companion object
}

fun ListUserRequest.Companion.qualifiedIds(qualifiedIDs: List<QualifiedID>) = QualifiedUserIdListRequest(qualifiedIDs)

@Serializable
data class QualifiedUserIdListRequest(
    @SerialName("qualified_ids") val qualifiedIds: List<QualifiedID>
) : ListUserRequest()

fun ListUserRequest.Companion.qualifiedHandles(qualifiedHandles: List<QualifiedHandle>) = QualifiedHandleListRequest(qualifiedHandles)

@Serializable
data class QualifiedHandleListRequest(
    @SerialName("qualified_handles") val qualifiedHandles: List<QualifiedHandle>
) : ListUserRequest()
