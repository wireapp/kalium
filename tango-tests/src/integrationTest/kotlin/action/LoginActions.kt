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
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.AuthenticationScope

object LoginActions {

    suspend fun loginAndAddAuthenticatedUser(
        email: String,
        password: String,
        coreLogic: CoreLogic,
        authScope: AuthenticationScope,
    ): AccountTokens {
        val loginResult = authScope.login(email, password, true)
        if (loginResult !is AuthenticationResult.Success) {
            error("User creds didn't work ($email, $password)")
        }

        coreLogic.globalScope {
            val storeResult = addAuthenticatedAccount(
                serverConfigId = loginResult.serverConfigId,
                ssoId = loginResult.ssoID,
                authTokens = loginResult.authData,
                proxyCredentials = loginResult.proxyCredentials,
                replace = true
            )
            if (storeResult !is AddAuthenticatedUserUseCase.Result.Success) {
                error("Failed to store user. $storeResult")
            }
        }

        return loginResult.authData
    }
}
