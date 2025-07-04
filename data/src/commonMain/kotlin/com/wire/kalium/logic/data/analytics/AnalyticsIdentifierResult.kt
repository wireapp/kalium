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

package com.wire.kalium.logic.data.analytics

sealed interface AnalyticsIdentifierResult {

    /**
     * To be used when there is no user logged in or analytics settings is disabled.
     */
    data object Disabled : AnalyticsIdentifierResult

    /**
     * Wrapper: To be used when there is a user logged in and analytics settings is enabled.
     */
    sealed interface Enabled : AnalyticsIdentifierResult {
        val identifier: String
    }

    /**
     * To be used when user first login to device, generating a new identifier and sending over to other clients.
     */
    data class NonExistingIdentifier(
        override val identifier: String
    ) : Enabled

    /**
     * To be used when user already has a tracking identifier, meaning no migration will be done.
     */
    data class ExistingIdentifier(
        override val identifier: String
    ) : Enabled

    /**
     * To be used when user is already logged in and receive a new tracking identifier from another client,
     * it needs to set received tracking identifier as current identifier with migration.
     * (migrate old identifier events to new identifier)
     */
    data class MigrationIdentifier(
        override val identifier: String
    ) : Enabled

    /**
     * To be used when we are registering a new potential user.
     */
    data class RegistrationIdentifier(override val identifier: String) : Enabled
}
