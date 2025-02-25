/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.LoginDomainPath
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginRedirectMapperTest {
    @Test
    fun givenADomainValue_whenMappingToResult_thenMapCorrectly() = runTest {
        val domainRegistrationMapper = LoginRedirectMapperImpl

        val result = domainRegistrationMapper.fromModelToResult(
            LoginDomainPath.Default
        )
        assertEquals(LoginRedirectPath.Default, result)
        assertEquals(true, result.isCloudAccountCreationPossible)
    }

    @Test
    fun givenADomainValueForCustomBackend_whenMappingToResult_thenMapCorrectly() = runTest {
        val domainRegistrationMapper = LoginRedirectMapperImpl

        val result = domainRegistrationMapper.fromModelToCustomBackendResult(
            LoginDomainPath.CustomBackend("my-custom-backend-url"),
            ServerConfig.STAGING
        )
        assertEquals(LoginRedirectPath.CustomBackend::class, result::class)
        assertEquals(ServerConfig.STAGING, (result as LoginRedirectPath.CustomBackend).serverLinks)
        assertEquals(false, result.isCloudAccountCreationPossible)
    }
}
