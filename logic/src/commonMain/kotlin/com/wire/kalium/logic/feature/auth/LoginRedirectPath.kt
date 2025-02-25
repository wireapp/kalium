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

sealed class LoginRedirectPath(val isCloudAccountCreationPossible: Boolean) {

    /**
     * Regular case for Wire cloud, where the user can login and create an account.
     * This is the default path and also covers pre-authorized and locked values.
     */
    data object Default : LoginRedirectPath(isCloudAccountCreationPossible = true)

    /**
     * SSO case for Wire cloud, where the user can login using SSO.
     * @param ssoCode the SSO code of a cloud team.
     */
    data class SSO(val ssoCode: String) : LoginRedirectPath(isCloudAccountCreationPossible = false)

    /**
     * The team has a custom backend, where the user can login.
     * @param serverLinks the URL of the json config from where to fetch the custom backend configurations.
     */
    data class CustomBackend(val serverLinks: ServerConfig.Links) : LoginRedirectPath(isCloudAccountCreationPossible = false)

    /**
     * Wire cloud case for users, they can login but not to create an account.
     */
    data object NoRegistration : LoginRedirectPath(isCloudAccountCreationPossible = false)

    /**
     * The user has an existing cloud account, but the domain is already claimed by an organization.
     */
    data class ExistingAccountWithClaimedDomain(val domain: String) : LoginRedirectPath(isCloudAccountCreationPossible = false)
}
