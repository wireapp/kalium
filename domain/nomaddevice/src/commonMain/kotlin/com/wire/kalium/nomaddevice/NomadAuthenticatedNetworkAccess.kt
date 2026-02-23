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

package com.wire.kalium.nomaddevice

import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkProvider

public class NomadAuthenticatedNetworkAccess(
    private val userAuthenticatedNetworkProvider: UserAuthenticatedNetworkProvider
) {
    public fun authenticatedNetworkContainer(userId: UserId): AuthenticatedNetworkContainer =
        userAuthenticatedNetworkProvider.get(userId)?.container
            ?: error(
                "AuthenticatedNetworkContainer for user '$userId' is missing. " +
                    "Initialize the user session in CoreLogic and inject the same " +
                    "UserAuthenticatedNetworkProvider instance into Logic and Nomad."
            )
}
