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

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi

internal interface MLSConversationsRecoveryManager {
    suspend fun invoke()
}

internal class MLSConversationsRecoveryManagerImpl(
    private val featureSupport: FeatureSupport,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val clientRepository: ClientRepository,
    private val recoverMLSConversationsUseCase: RecoverMLSConversationsUseCase,
    private val slowSyncRepository: SlowSyncRepository,
) : MLSConversationsRecoveryManager {

    @Suppress("ComplexCondition")
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun invoke() {
        // wait until incremental sync is done
        incrementalSyncRepository.incrementalSyncState.collect { syncState ->
            if (syncState is IncrementalSyncStatus.Live &&
                featureSupport.isMLSSupported &&
                clientRepository.hasRegisteredMLSClient().getOrElse(false) &&
                slowSyncRepository.needsToRecoverMLSGroups()
            ) {
                recoverMLSConversations()
            }
        }
    }

    private suspend fun recoverMLSConversations() =
        recoverMLSConversationsUseCase.invoke().let { result ->
            when (result) {
                is RecoverMLSConversationsResult.Failure ->
                    kaliumLogger.w("Error while recovering MLS conversations: ${result.failure}")
                is RecoverMLSConversationsResult.Success ->
                    slowSyncRepository.setNeedsToRecoverMLSGroups(false)
            }
        }
}
