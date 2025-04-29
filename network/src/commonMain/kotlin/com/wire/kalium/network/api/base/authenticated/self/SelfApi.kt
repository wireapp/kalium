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

package com.wire.kalium.network.api.base.authenticated.self

import com.wire.kalium.network.api.model.SelfUserDTO
import com.wire.kalium.network.api.model.SupportedProtocolDTO
import com.wire.kalium.network.api.base.authenticated.BaseApi
import com.wire.kalium.network.api.authenticated.self.UserUpdateRequest
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mockable
import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class ChangeHandleRequest(
    @SerialName("handle") val handle: String
)

@Mockable
interface SelfApi : BaseApi {
    suspend fun getSelfInfo(): NetworkResponse<SelfUserDTO>
    suspend fun updateSelf(userUpdateRequest: UserUpdateRequest): NetworkResponse<Unit>
    suspend fun changeHandle(request: ChangeHandleRequest): NetworkResponse<Unit>

    /**
     * Update the email address of the current user.
     * @param email The new email address.
     * @return A [NetworkResponse] with the result of the operation.
     * true if the email address was updated, it is the same email address
     */
    suspend fun updateEmailAddress(email: String): NetworkResponse<Boolean>
    suspend fun deleteAccount(password: String?): NetworkResponse<Unit>

    /**
     * Update the supported protocols of the current user.
     * @param protocols The updated list of supported protocols.
     * @return A [NetworkResponse] with the result of the operation.
     * true if the protocols were updated.
     */
    suspend fun updateSupportedProtocols(protocols: List<SupportedProtocolDTO>): NetworkResponse<Unit>
}
