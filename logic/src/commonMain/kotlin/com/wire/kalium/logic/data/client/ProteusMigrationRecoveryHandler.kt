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

import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.kaliumLogger

/**
 * Handles the migration of Proteus storage.
 * Meaning, when migration is performed in case there is an error, this handler will be responsible for handling it.
 */
interface ProteusMigrationRecoveryHandler {
    suspend fun clearClientData(clearLocalFiles: suspend () -> Unit)
}

internal class ProteusMigrationRecoveryHandlerImpl(
    private val logoutUseCase: Lazy<LogoutUseCase>
) : ProteusMigrationRecoveryHandler {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun clearClientData(clearLocalFiles: suspend () -> Unit) {
        // pending? remove most recent prekey id
        kaliumLogger.withTextTag(TAG).i("Starting the recovery from failed Proteus storage migration")
        try {
            logoutUseCase.value(LogoutReason.REMOVED_CLIENT, true)
            clearLocalFiles()
        } catch (e: Exception) {
            kaliumLogger.e("$TAG - Fatal, error while clearing client data: $e")
        } finally {
            kaliumLogger.withTextTag(TAG).i("Finished the recovery from failed Proteus storage migration")
        }
    }

    private companion object {
        const val TAG = "ProteusMigrationRecoveryHandler"
    }
}
