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
package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.feature.CachedClientIdClearer
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.runBlocking

/**
 * Handles the migration of Proteus storage.
 * Meaning, when migration is performed in case there is an error, this handler will be responsible for handling it.
 */
interface ProteusMigrationRecoveryHandler {
    suspend fun clearClientData()
}

internal class ProteusMigrationRecoveryHandlerImpl(
    private val clientRepository: ClientRepository,
    private val pushTokenRepository: PushTokenRepository,
    private val cachedClientIdClearer: CachedClientIdClearer,
) : ProteusMigrationRecoveryHandler {

    override suspend fun clearClientData(): Unit = runBlocking {
        // pending? remove most recent prekey id
        try {
            kaliumLogger.d("Starting to clear client cached clientId")
            cachedClientIdClearer()
//             kaliumLogger.d("Starting to clear current client id")
//             clientRepository.clearCurrentClientId()
            kaliumLogger.d("Starting to clear retained client id")
            clientRepository.clearRetainedClientId()
            kaliumLogger.d("Starting to clear firebase token")
            pushTokenRepository.setUpdateFirebaseTokenFlag(true)
        } catch (e: Exception) {
            kaliumLogger.e("Error clearing client data: $e")
        }
    }
}
