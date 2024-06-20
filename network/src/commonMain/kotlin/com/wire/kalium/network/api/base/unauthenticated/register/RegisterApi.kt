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

package com.wire.kalium.network.api.base.unauthenticated.register

import com.wire.kalium.network.api.model.SelfUserDTO
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.unauthenticated.register.ActivationParam
import com.wire.kalium.network.api.unauthenticated.register.RegisterParam
import com.wire.kalium.network.api.unauthenticated.register.RequestActivationCodeParam
import com.wire.kalium.network.utils.NetworkResponse

interface RegisterApi {

    suspend fun register(
        param: RegisterParam
    ): NetworkResponse<Pair<SelfUserDTO, SessionDTO>>

    suspend fun requestActivationCode(
        param: RequestActivationCodeParam
    ): NetworkResponse<Unit>

    suspend fun activate(
        param: ActivationParam
    ): NetworkResponse<Unit>
}
