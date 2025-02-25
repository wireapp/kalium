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
package com.wire.kalium.logic.data.auth.login

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.LoginDomainPath
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.LoginRedirectPath
import com.wire.kalium.network.api.unauthenticated.domainregistration.DomainRedirect
import com.wire.kalium.network.api.unauthenticated.domainregistration.DomainRegistrationDTO
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainRegistrationMapperTest {

    @Test
    fun givenADomainRedirectValueDTO_whenMappingToDomain_thenMapCorrectly() = runTest {
        val domainRegistrationMapper = MapperProvider.domainRegistrationMapper()

        val result = domainRegistrationMapper.fromApiModel(
            Arrangement.provideDomainRegistrationDTO(DomainRedirect.NONE), Arrangement.EMAIL
        )
        assertEquals(LoginDomainPath.Default, result)
    }

    @Test
    fun givenADomainRedirectValueDTOSSO_whenMappingToDomain_thenMapCorrectly() = runTest {
        val ssoCode = "some-sso-code"
        val domainRegistrationMapper = MapperProvider.domainRegistrationMapper()

        val result = domainRegistrationMapper.fromApiModel(
            Arrangement.provideDomainRegistrationDTO(DomainRedirect.SSO, ssoCode = ssoCode), Arrangement.EMAIL
        )
        assertEquals(LoginDomainPath.SSO::class, result::class)
        assertEquals(ssoCode, (result as LoginDomainPath.SSO).ssoCode)
    }

    @Test
    fun givenADomainRedirectValueDTOCustomBackend_whenMappingToDomain_thenMapCorrectly() = runTest {
        val backendUrl = "my-custom-backend-url"
        val domainRegistrationMapper = MapperProvider.domainRegistrationMapper()

        val result = domainRegistrationMapper.fromApiModel(
            Arrangement.provideDomainRegistrationDTO(DomainRedirect.BACKEND, backendUrl = backendUrl), Arrangement.EMAIL
        )
        assertEquals(LoginDomainPath.CustomBackend::class, result::class)
        assertEquals(backendUrl, (result as LoginDomainPath.CustomBackend).backendConfigUrl)
    }

    @Test
    fun givenADomainRedirectValueDTONoneAndAccountExists_whenMappingToDomain_thenMapCorrectly() = runTest {
        val domainRegistrationMapper = MapperProvider.domainRegistrationMapper()

        val result = domainRegistrationMapper.fromApiModel(
            Arrangement.provideDomainRegistrationDTO(DomainRedirect.NONE, dueToExistingAccount = true), Arrangement.EMAIL
        )
        assertEquals(LoginDomainPath.ExistingAccountWithClaimedDomain::class, result::class)
        assertEquals("wire.com", (result as LoginDomainPath.ExistingAccountWithClaimedDomain).domain)
        assertEquals(false, result.isCloudAccountCreationPossible)
    }



    private object Arrangement {

        const val EMAIL = "user@wire.com"

        fun provideDomainRegistrationDTO(
            domainRedirect: DomainRedirect,
            dueToExistingAccount: Boolean? = null,
            ssoCode: String? = null,
            backendUrl: String? = null
        ): DomainRegistrationDTO {
            return DomainRegistrationDTO(
                domainRedirect = domainRedirect,
                dueToExistingAccount = dueToExistingAccount,
                ssoCode = ssoCode,
                backendUrl = backendUrl
            )
        }
    }
}
