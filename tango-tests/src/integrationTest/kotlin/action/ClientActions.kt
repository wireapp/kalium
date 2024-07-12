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
package action

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.client.RegisterClientUseCase

object ClientActions {

    suspend fun registerClient(
        password: String,
        userId: UserId,
        coreLogic: CoreLogic
    ): UserSessionScope {
        val userSession = coreLogic.getSessionScope(userId)
        val registerClientParam = RegisterClientUseCase.RegisterClientParam(
            password = password,
            capabilities = emptyList(),
            clientType = ClientType.Temporary
        )
        val registerResult = userSession.client.getOrRegister(registerClientParam)
        if (registerResult is RegisterClientResult.Failure) {
            error("Failed registering client of monkey : $registerResult")
        }

        return userSession
    }

}
