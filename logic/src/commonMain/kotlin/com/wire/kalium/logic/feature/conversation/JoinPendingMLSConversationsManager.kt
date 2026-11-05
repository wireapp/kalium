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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.keypackage.KeyPackageManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Joins pending MLS conversations by external commit after this client may have been left out of MLS groups:
 * it ran out of key packages, or it registered its MLS client after conversations were migrated to MLS.
 *
 * Runs only while incremental sync is live, so every Welcome already sent to this client has been processed and
 * only conversations still in a pending state are joined. The flag is cleared after a successful join, otherwise
 * it is retried the next time sync becomes live.
 */
internal interface JoinPendingMLSConversationsManager {
    suspend operator fun invoke()
}

internal class JoinPendingMLSConversationsManagerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val userConfigRepository: UserConfigRepository,
    private val joinExistingMLSConversations: JoinExistingMLSConversationsUseCase,
    private val keyPackageManager: KeyPackageManager,
    kaliumLogger: KaliumLogger,
) : JoinPendingMLSConversationsManager {

    private val logger = kaliumLogger.withTextTag("JoinPendingMLSConversationsManager")

    override suspend fun invoke() {
        combine(
            incrementalSyncRepository.incrementalSyncState,
            userConfigRepository.observeShouldJoinPendingMLSConversations()
        ) { syncState, shouldJoin -> syncState is IncrementalSyncStatus.Live && shouldJoin }
            .distinctUntilChanged()
            .filter { it }
            .collect { joinPendingConversations() }
    }

    private suspend fun joinPendingConversations() {
        logger.i("Joining pending MLS conversations by external commit")
        var pendingConversations = 0
        val joined = joinExistingMLSConversations(
            keepRetryingOnFailure = true,
            allowJoinByExternalCommit = true,
            includePendingWelcome = true,
            onProgress = { pendingConversations = it.total },
        )
        // This client was left out of a group, maybe because other clients had claimed all its key packages. Check
        // before clearing the request: finding no key packages raises it again, and this pass has already run.
        if (pendingConversations > 0) keyPackageManager.checkKeyPackageCount()
        joined.flatMap {
            userConfigRepository.setShouldJoinPendingMLSConversations(false)
        }.fold(
            { logger.w("Failed to join pending MLS conversations, retrying when sync is live again: $it") },
            { logger.i("Joined pending MLS conversations") }
        )
    }
}
