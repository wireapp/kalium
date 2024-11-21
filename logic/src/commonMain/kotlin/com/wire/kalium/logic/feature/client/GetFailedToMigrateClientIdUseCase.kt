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
package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId

/**
 * Use case to get the client id that failed to migrate to core crypto.
 * The usage of this can be extended, but the intent is to filter this client out from the list of clients.
 *
 * This is needed since we can not remove this while we don't register with backend a new one for token refresh.
 */
interface GetFailedToMigrateClientIdUseCase {
    suspend operator fun invoke(): ClientId?
}

@Suppress("FunctionNaming")
internal fun GetFailedToMigrateClientIdUseCase(
    clientRepository: ClientRepository
): GetFailedToMigrateClientIdUseCase = object : GetFailedToMigrateClientIdUseCase {
    override suspend fun invoke(): ClientId? = clientRepository.getClientIdWithMigrationToCCFailure()
}
