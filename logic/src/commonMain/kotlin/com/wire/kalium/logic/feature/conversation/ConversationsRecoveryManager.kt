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
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCase

internal interface ConversationsRecoveryManager {
    suspend fun invoke()
}

internal class ConversationsRecoveryManagerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val addSystemMessageToAllConversationsUseCase: AddSystemMessageToAllConversationsUseCase,
    private val slowSyncRepository: SlowSyncRepository,
    kaliumLogger: KaliumLogger
) : ConversationsRecoveryManager {

    private val logger = kaliumLogger.withTextTag("ConversationsRecoveryManager")

    @Suppress("ComplexCondition")
    override suspend fun invoke() {
        logger.d("Starting to monitor")
        // wait until incremental sync is done
        incrementalSyncRepository.incrementalSyncState.collect { syncState ->
            if (syncState is IncrementalSyncStatus.Live &&
                slowSyncRepository.needsToPersistHistoryLostMessage()
            ) {
                logger.i("Persisting history lost message and marking it as no longer needed")
                addSystemMessageToAllConversationsUseCase.invoke()
                slowSyncRepository.setNeedsToPersistHistoryLostMessage(false)
            }
        }
    }

}
