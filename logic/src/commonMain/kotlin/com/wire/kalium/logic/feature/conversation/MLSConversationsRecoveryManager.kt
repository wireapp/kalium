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

import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logic.data.client.CryptoTransactionProvider

internal interface MLSConversationsRecoveryManager {
    suspend fun invoke()
}

@Suppress("LongParameterList")
internal class MLSConversationsRecoveryManagerImpl(
    private val featureSupport: FeatureSupport,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val clientRepository: ClientRepository,
    private val recoverMLSConversationsUseCase: RecoverMLSConversationsUseCase,
    private val slowSyncRepository: SlowSyncRepository,
    private val transactionProvider: CryptoTransactionProvider,
    kaliumLogger: KaliumLogger
) : MLSConversationsRecoveryManager {

    private val logger = kaliumLogger.withTextTag("MLSConversationRecoveryManager")

    @Suppress("ComplexCondition")
    override suspend fun invoke() {
        // wait until incremental sync is done
        logger.v("Starting to observe for matching requirements")
        incrementalSyncRepository.incrementalSyncState.collect { syncState ->
            val isMlsSupported = featureSupport.isMLSSupported
            val hasRegisteredMLSClient = clientRepository.hasRegisteredMLSClient().getOrElse(false)
            val needsToRecoverMLSGroups = slowSyncRepository.needsToRecoverMLSGroups()
            logger.logStructuredJson(
                level = KaliumLogLevel.DEBUG,
                leadingMessage = "SyncState updated",
                jsonStringKeyValues = mapOf(
                    "isMlsSupported" to isMlsSupported,
                    "hasRegisteredMLSClient" to hasRegisteredMLSClient,
                    "needsToRecoverMLSGroups" to needsToRecoverMLSGroups
                )
            )
            if (syncState is IncrementalSyncStatus.Live &&
                isMlsSupported &&
                hasRegisteredMLSClient &&
                needsToRecoverMLSGroups
            ) {
                logger.i("Recovering MLS Conversations")
                recoverMLSConversations()
            }
        }
    }

    private suspend fun recoverMLSConversations() = transactionProvider.transaction { transactionContext ->
        recoverMLSConversationsUseCase.invoke(transactionContext).let { result ->
            when (result) {
                is RecoverMLSConversationsResult.Failure -> {
                    logger.w("Error while recovering MLS conversations: ${result.failure}")
                    result.failure.left()
                }
                is RecoverMLSConversationsResult.Success -> {
                    logger.i("Successfully recovered MLS Conversations")
                    slowSyncRepository.setNeedsToRecoverMLSGroups(false)
                    Unit.right()
                }
            }
        }
    }
}
