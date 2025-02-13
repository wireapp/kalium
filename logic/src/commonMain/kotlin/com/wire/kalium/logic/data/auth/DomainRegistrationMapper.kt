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
package com.wire.kalium.logic.data.auth

import com.wire.kalium.network.api.unauthenticated.domainregistration.DomainRedirect
import com.wire.kalium.network.api.unauthenticated.domainregistration.DomainRegistrationDTO

internal interface DomainRegistrationMapper {
    fun fromApiModel(domainRegistrationDTO: DomainRegistrationDTO, email: String): LoginDomainPath
}

internal object DomainRegistrationMapperImpl : DomainRegistrationMapper {
    override fun fromApiModel(domainRegistrationDTO: DomainRegistrationDTO, email: String): LoginDomainPath {
        return when (domainRegistrationDTO.domainRedirect) {
            DomainRedirect.PRE_AUTHORIZED -> LoginDomainPath.Default
            DomainRedirect.LOCKED -> LoginDomainPath.Default
            DomainRedirect.NONE -> {
                if (domainRegistrationDTO.dueToExistingAccount == true) {
                    LoginDomainPath.ExistingAccountWithClaimedDomain(extractDomain(email))
                } else {
                    LoginDomainPath.Default
                }
            }

            DomainRedirect.SSO -> LoginDomainPath.SSO(domainRegistrationDTO.ssoCode!!)
            DomainRedirect.BACKEND -> LoginDomainPath.CustomBackend(domainRegistrationDTO.backendUrl!!)
            DomainRedirect.NO_REGISTRATION -> LoginDomainPath.NoRegistration
        }
    }

    private fun extractDomain(email: String): String {
        return email.substringAfter('@')
    }
}
