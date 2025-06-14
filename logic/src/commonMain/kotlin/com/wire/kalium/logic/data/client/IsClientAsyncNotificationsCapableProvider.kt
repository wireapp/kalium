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
package com.wire.kalium.logic.data.client

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import io.mockative.Mockable
import kotlinx.coroutines.flow.firstOrNull

/**
 * This provider checks if the client is capable of async notifications.
 * It does this by checking if the client has consumable notifications capability.
 *
 * At some point Legacy notifications will be deprecated and removed from the codebase.
 */
internal class IsClientAsyncNotificationsCapableProviderImpl(
    private val clientRegistrationStorage: ClientRegistrationStorage
) : IsClientAsyncNotificationsCapableProvider {

    override suspend fun invoke(): Either<CoreFailure, Boolean> {
        return wrapStorageRequest {
            clientRegistrationStorage.observeHasConsumableNotifications().firstOrNull() == true
        }
    }

}

@Mockable
internal fun interface IsClientAsyncNotificationsCapableProvider {
    suspend operator fun invoke(): Either<CoreFailure, Boolean>
}
