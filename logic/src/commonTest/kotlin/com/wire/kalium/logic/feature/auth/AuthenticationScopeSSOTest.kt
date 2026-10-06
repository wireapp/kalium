/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.feature.auth.sso.SSOLoginSessionResult
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.TestRequestHandler
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.TestGlobalDatabase
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthenticationScopeSSOTest {
    @Test
    fun pendingCodeLoginUsesSelectedBackendWithoutOpeningGlobalOrAccountStorage() = runTest {
        for (version in 16..18) {
            val scope = scope(version, """{"ssoIdpChangeDetectionEnabled":true}""")
            // Every fetch gets a dedicated engine: closing one pending request must not break the next login.
            repeat(2) {
                val result = scope.ssoLoginScope.getLoginSession("zuid=fresh-cookie", true)
                assertIs<SSOLoginSessionResult.Success>(result)
                assertTrue(result.isIdpChangeDetectionEnabled)
                assertEquals("fresh-cookie", result.accountTokens.refreshToken.value)
                assertEquals("fresh-token", result.accountTokens.accessToken.value)
                assertEquals(proxy, result.proxyCredentials)
            }
        }
    }

    @Test
    fun absentCapabilityIsFalseAndLegacyLoginSkipsFailedSettings() = runTest {
        val absentResult = scope(18, "{}").ssoLoginScope.getLoginSession("zuid=fresh-cookie", true)
        assertIs<SSOLoginSessionResult.Success>(absentResult)
        assertFalse(absentResult.isIdpChangeDetectionEnabled)

        val legacyResult = scope(18, "{}", HttpStatusCode.InternalServerError)
            .ssoLoginScope.getLoginSession("zuid=fresh-cookie")
        assertIs<SSOLoginSessionResult.Success>(legacyResult)
        assertFalse(legacyResult.isIdpChangeDetectionEnabled)
    }

    @Test
    fun settingsFailurePreventsReturningPendingLoginTokens() = runTest {
        val result = scope(18, "{}", HttpStatusCode.InternalServerError)
            .ssoLoginScope.getLoginSession("zuid=fresh-cookie", true)
        assertIs<SSOLoginSessionResult.Failure.Generic>(result)
    }

    private val proxy = ProxyCredentials("proxy-user", "proxy-password")

    private fun scope(version: Int, settings: String, settingsStatus: HttpStatusCode = HttpStatusCode.OK) = AuthenticationScope(
        userAgent = "test/useragent",
        serverConfig = newServerConfig(version),
        proxyCredentials = proxy,
        // The closed database cannot service a query. Pending login has no account resource dependency.
        globalDatabase = closedGlobalDatabase(),
        kaliumConfigs = KaliumConfigs(mockedRequests = listOf(
            TestRequestHandler(
                path = "/v$version/access",
                responseBody = """{"user":"${TestUser.SELF_USER_DTO.id.value}","access_token":"fresh-token","token_type":"Bearer","expires_in":3600}""",
                statusCode = HttpStatusCode.OK,
                httpMethod = HttpMethod.Post,
            ),
            TestRequestHandler(
                path = "/v$version/self",
                responseBody = KtxSerializer.json.encodeToString(TestUser.SELF_USER_DTO),
                statusCode = HttpStatusCode.OK,
                httpMethod = HttpMethod.Get,
            ),
            TestRequestHandler(
                path = "/v$version/system/settings",
                responseBody = settings,
                statusCode = settingsStatus,
                httpMethod = HttpMethod.Get,
            ),
        )),
    )
    private fun closedGlobalDatabase(): GlobalDatabaseBuilder {
        val database = TestGlobalDatabase()
        val provider = database.provider
        provider.close()
        database.delete()
        return provider
    }
}
