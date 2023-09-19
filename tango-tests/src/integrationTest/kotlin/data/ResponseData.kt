/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package data

import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.model.SelfUserDTO
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import util.AccessTokenDTOJson
import util.ClientResponseJson
import util.ErrorResponseJson
import util.ListOfClientsResponseJson
import util.ListUsersResponseJson
import util.LoginWithEmailRequestJson
import util.MockUnboundNetworkClient
import util.ServerConfigDTOJson
import util.UserDTOJson

/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
object ResponseData {

    /**
     * URL Paths
     */
    const val BASE_PATH = "https://test.api.com/v1/"
    const val PATH_LOGIN = "${BASE_PATH}login?persist=true"
    const val PATH_SELF = "${BASE_PATH}self"
    const val PATH_LOGOUT = "${BASE_PATH}logout"
    const val PATH_API_VERSION = "${BASE_PATH}api-version"
    const val PATH_CLIENTS = "${BASE_PATH}clients"
    const val PATH_ACCESS = "${BASE_PATH}access"
    const val ACME_DIRECTORIES_PATH = "https://balderdash.hogwash.work:9000/acme/google-android/directory"

    /**
     * DTO
     */
    const val REFRESH_TOKEN = "415a5306-a476-41bc-af36-94ab075fd881"
    val userID = QualifiedID("user_id", "user.domain.io")
    val accessTokenDTO = AccessTokenDTO(
        userId = userID.value,
        value = "Nlrhltkj-NgJUjEVevHz8Ilgy_pyWCT2b0kQb-GlnamyswanghN9DcC3an5RUuA7sh1_nC3hv2ZzMRlIhPM7Ag==.v=1.k=1.d=1637254939." +
                "t=a.l=.u=75ebeb16-a860-4be4-84a7-157654b492cf.c=18401233206926541098",
        expiresIn = 900,
        tokenType = "Bearer"
    )
    val userDTO = SelfUserDTO(
        id = userID,
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
        ssoID = null
    )

    /**
     * JSON Response
     */
    val VALID_ACCESS_TOKEN_RESPONSE = AccessTokenDTOJson.createValid(accessTokenDTO)
    val VALID_SELF_RESPONSE = UserDTOJson.createValid(userDTO)

    val LOGIN_WITH_EMAIL_REQUEST = LoginWithEmailRequestJson.validLoginWithEmail
    val ERROR_RESPONSE = ErrorResponseJson.valid.serializableData
    val listUsersResponseJson = ListUsersResponseJson.v0
    val ACME_DIRECTORIES_RESPONSE = ACMEApiResponseJsonSample.validAcmeDirectoriesResponse
    val ACME_DIRECTORIES_SAMPLE = ACMEApiResponseJsonSample.ACME_DIRECTORIES_SAMPLE

    /**
     * Request / Responses
     */
    val loginRequestSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = PATH_LOGIN,
        responseBody = VALID_ACCESS_TOKEN_RESPONSE.rawJson,
        statusCode = HttpStatusCode.OK,
        httpMethod = HttpMethod.Post,
        headers = mapOf("set-cookie" to "zuid=$REFRESH_TOKEN")
    )

    val selfRequestSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = PATH_SELF,
        responseBody = VALID_SELF_RESPONSE.rawJson,
        httpMethod = HttpMethod.Get,
        statusCode = HttpStatusCode.OK,
    )

    val userDetailsRequestSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = PATH_LOGOUT,
        responseBody = listUsersResponseJson.rawJson,
        httpMethod = HttpMethod.Post,
        statusCode = HttpStatusCode.Forbidden,
    )

    val apiVersionRequestSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = PATH_API_VERSION,
        responseBody = ServerConfigDTOJson.validServerConfigResponse.rawJson,
        httpMethod = HttpMethod.Get,
        statusCode = HttpStatusCode.OK,
    )

    val registerClientsRequestSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = PATH_CLIENTS,
        httpMethod = HttpMethod.Post,
        responseBody = ClientResponseJson.valid.rawJson,
        statusCode = HttpStatusCode.OK,
    )
    val getClientsRequestSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = PATH_CLIENTS,
        httpMethod = HttpMethod.Get,
        responseBody = ListOfClientsResponseJson.valid.rawJson,
        statusCode = HttpStatusCode.OK,
    )
    val accessApiRequestSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = PATH_ACCESS,
        httpMethod = HttpMethod.Post,
        responseBody = VALID_ACCESS_TOKEN_RESPONSE.rawJson,
        statusCode = HttpStatusCode.OK,
    )
    val acmeGetDirectoriesRequestSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = ACME_DIRECTORIES_PATH,
        httpMethod = HttpMethod.Get,
        responseBody = ACME_DIRECTORIES_RESPONSE.rawJson,
        statusCode = HttpStatusCode.OK
    )
}
