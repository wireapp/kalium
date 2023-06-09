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
package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScopeProvider

/**
 * Clear all NewClients for the [UserId] from the BD.
 * Use it after informing user about NewClients, or when user logs out.
 */
interface ClearNewClientsForUserUseCase {
    suspend operator fun invoke(userId: UserId)
}

class ClearNewClientsForUserUseCaseImpl(private val userSessionScopeProvider: UserSessionScopeProvider) : ClearNewClientsForUserUseCase {
    override suspend fun invoke(userId: UserId) {
        userSessionScopeProvider.getOrCreate(userId)
            .clientRepository
            .clearNewClients()
    }
}
