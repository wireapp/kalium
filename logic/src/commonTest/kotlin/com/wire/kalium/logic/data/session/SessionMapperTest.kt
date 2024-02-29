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

package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.network.api.base.model.SessionDTO
import com.wire.kalium.persistence.client.AuthTokenEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.model.SsoIdEntity
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import com.wire.kalium.network.api.base.model.UserId as UserIdDTO

class SessionMapperTest {

    private lateinit var sessionMapper: SessionMapper

    @BeforeTest
    fun setup() {
        sessionMapper = SessionMapperImpl()
    }

    @Test
    fun givenAnAuthTokens_whenMappingToSessionCredentials_thenValuesAreMappedCorrectly() {
        val authSession: AccountTokens = TEST_AUTH_TOKENS

        val acuteValue: SessionDTO =
            with(authSession) {
                SessionDTO(
                    UserIdDTO(userId.value, userId.domain),
                    tokenType,
                    accessToken.value,
                    refreshToken.value,
                    cookieLabel
                )
            }

        val expectedValue: SessionDTO = sessionMapper.toSessionDTO(authSession)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenAnAuthTokens_whenMappingToPersistenceAuthTokens_thenValuesAreMappedCorrectly() {
        val authSession: AccountTokens = TEST_AUTH_TOKENS

        val expected: AuthTokenEntity = with(authSession) {
            AuthTokenEntity(
                userId = UserIDEntity(userId.value, userId.domain),
                tokenType = tokenType,
                accessToken = accessToken.value,
                refreshToken = refreshToken.value,
                cookieLabel = cookieLabel
            )
        }

        val actual: AuthTokenEntity = sessionMapper.toAuthTokensEntity(authSession)
        assertEquals(expected, actual)
    }

    private companion object {
        val userId = UserId("user_id", "user.domain.io")

        val TEST_AUTH_TOKENS = AccountTokens(
            userId = userId,
            tokenType = "Bearer",
            accessToken = "access_token",
            refreshToken = "refresh_token",
            cookieLabel = "cookie_label"
        )

        val TEST_SSO_ID = SsoId("scim_external", "subject", null)
        val TEST_SSO_ID_ENTITY = SsoIdEntity("scim_external", "subject", null)
    }
}
