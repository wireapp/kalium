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

package com.wire.kalium.network.api.base.unauthenticated

import com.wire.kalium.network.api.base.model.AuthenticationResultDTO
import com.wire.kalium.network.utils.NetworkResponse

interface SSOLoginApi {

    sealed class InitiateParam(open val uuid: String) {
        data class WithoutRedirect(override val uuid: String) : InitiateParam(uuid)
        data class WithRedirect(val success: String, val error: String, override val uuid: String) : InitiateParam(uuid)
    }

    suspend fun initiate(param: InitiateParam): NetworkResponse<String>

    suspend fun finalize(cookie: String): NetworkResponse<String>

    suspend fun provideLoginSession(cookie: String): NetworkResponse<AuthenticationResultDTO>

    // TODO(web): ask about the response model since it's xml in swagger with no model
    suspend fun metaData(): NetworkResponse<String>

    suspend fun settings(): NetworkResponse<SSOSettingsResponse>
}
