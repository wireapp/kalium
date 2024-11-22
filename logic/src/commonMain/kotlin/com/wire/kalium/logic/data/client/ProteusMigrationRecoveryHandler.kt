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

interface ProteusMigrationRecoveryHandler {
    suspend fun clearClientData()
}

internal class ProteusMigrationRecoveryHandlerImpl(
    private val logoutUseCase: Lazy<LogoutUseCase>
) : ProteusMigrationRecoveryHandler {

    /**
     * Handles the migration error of a proteus client storage from CryptoBox to CoreCrypto.
     * It will perform a logout, using [LogoutReason.MIGRATION_TO_CC_FAILED] as the reason.
     *
     * This achieves that the client data is cleared and the user is logged out without losing content.
     */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun clearClientData() {
        try {
            kaliumLogger.withTextTag(TAG).i("Starting the recovery from failed Proteus storage migration")
            logoutUseCase.value(LogoutReason.MIGRATION_TO_CC_FAILED, true)
        } catch (e: Exception) {
            kaliumLogger.withTextTag(TAG).e("Fatal, error while clearing client data: $e")
        } finally {
            kaliumLogger.withTextTag(TAG).i("Finished the recovery from failed Proteus storage migration")
        }
    }

    private companion object {
        const val TAG = "ProteusMigrationRecoveryHandler"
    }
}
