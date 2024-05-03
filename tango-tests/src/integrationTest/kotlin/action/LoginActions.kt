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
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.network.api.base.model.SelfUserDTO
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import util.ListUsersResponseJson
import util.MockUnboundNetworkClient
import util.ServerConfigDTOJson
import util.UserDTOJson

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

    /**
     * URL Paths
     */
    private const val PATH_LOGIN = "${CommonResponses.BASE_PATH_V1}login?persist=true"
    private const val PATH_SELF = "${CommonResponses.BASE_PATH_V1}self"
    private const val PATH_LOGOUT = "${CommonResponses.BASE_PATH_V1}logout"
    private const val PATH_API_VERSION = "${CommonResponses.BASE_PATH}api-version"

    /**
     * JSON Response
     */
    private val listUsersResponseJson = ListUsersResponseJson.v0
    private val selfUserDTO = SelfUserDTO(
        id = CommonResponses.userID,
        name = "user_name_123",
        accentId = 2,
        assets = listOf(),
        deleted = null,
        email = "test@testio.test",
        handle = "mrtestio",
        service = null,
        teamId = null,
        expiresAt = "2026-03-25T14:17:27.364Z",
        nonQualifiedId = "",
        locale = "",
        managedByDTO = null,
        phone = null,
        ssoID = null,
        supportedProtocols = null
    )
    private val VALID_SELF_RESPONSE = UserDTOJson.createValid(selfUserDTO)

    /**
     * Requests / Responses
     */
    private val loginRequestSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = PATH_LOGIN,
        responseBody = CommonResponses.VALID_ACCESS_TOKEN_RESPONSE.rawJson,
        statusCode = HttpStatusCode.OK,
        httpMethod = HttpMethod.Post,
        headers = mapOf("set-cookie" to "zuid=${CommonResponses.REFRESH_TOKEN}")
    )

    private val selfRequestSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = PATH_SELF,
        responseBody = VALID_SELF_RESPONSE.rawJson,
        httpMethod = HttpMethod.Get,
        statusCode = HttpStatusCode.OK,
    )

    private val userDetailsRequestSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = PATH_LOGOUT,
        responseBody = listUsersResponseJson.rawJson,
        httpMethod = HttpMethod.Post,
        statusCode = HttpStatusCode.Forbidden,
    )

    private val apiVersionRequestSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = PATH_API_VERSION,
        responseBody = ServerConfigDTOJson.validServerConfigResponse.rawJson,
        httpMethod = HttpMethod.Get,
        statusCode = HttpStatusCode.OK,
    )

    val loginRequestResponseSuccess = listOf(
        loginRequestSuccess,
        selfRequestSuccess,
        userDetailsRequestSuccess,
        apiVersionRequestSuccess
    )
}
