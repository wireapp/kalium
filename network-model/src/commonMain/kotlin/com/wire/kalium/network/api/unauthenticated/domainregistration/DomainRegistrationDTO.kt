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
package com.wire.kalium.network.api.unauthenticated.domainregistration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DomainRegistrationDTO(
    @SerialName("backend_url")
    val backendUrl: String?,
    @SerialName("domain_redirect")
    val domainRedirect: DomainRedirect,
    @SerialName("sso_code")
    val ssoCode: String?,
    @SerialName("due_to_existing_account")
    val dueToExistingAccount: Boolean?
)

@Serializable
enum class DomainRedirect {
    @SerialName("none")
    NONE,

    @SerialName("locked")
    LOCKED,

    @SerialName("sso")
    SSO,

    @SerialName("backend")
    BACKEND,

    @SerialName("no-registration")
    NO_REGISTRATION,

    @SerialName("pre-authorized")
    PRE_AUTHORIZED;

    override fun toString(): String {
        return when (this) {
            NONE -> "none"
            LOCKED -> "locked"
            SSO -> "sso"
            BACKEND -> "backend"
            NO_REGISTRATION -> "no-registration"
            PRE_AUTHORIZED -> "pre-authorized"
        }
    }
}
