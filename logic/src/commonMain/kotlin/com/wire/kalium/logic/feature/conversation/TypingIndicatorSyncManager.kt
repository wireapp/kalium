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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.TypingIndicatorIncomingRepository
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

internal class TypingIndicatorSyncManager(
    private val typingIndicatorIncomingRepository: Lazy<TypingIndicatorIncomingRepository>,
    private val observeSyncStateUseCase: ObserveSyncStateUseCase,
    kaliumLogger: KaliumLogger
) {
    private val logger = kaliumLogger.withTextTag("TypingIndicatorSyncManager")
    /**
     * Periodically clears and drop orphaned typing indicators, so we don't keep them forever.
     */
    suspend fun execute() {
        logger.d("Starting to monitor")
        observeSyncStateUseCase().distinctUntilChanged().collectLatest {
            logger.d("Starting clear of orphaned typing indicators...")
            typingIndicatorIncomingRepository.value.clearExpiredTypingIndicators()
        }
    }
}
