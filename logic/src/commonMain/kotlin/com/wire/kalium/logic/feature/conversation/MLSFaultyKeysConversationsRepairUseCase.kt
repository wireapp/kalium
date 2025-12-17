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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.debug.RepairFaultyRemovalKeysUseCase
import com.wire.kalium.logic.feature.debug.RepairResult
import com.wire.kalium.logic.feature.debug.TargetedRepairParam
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import kotlinx.coroutines.flow.filter

/**
 * Internal use case to repair conversations with faulty MLS keys based on known faulty keys per domain.
 * This is intended to be run once after an app update that includes the fix for the faulty keys.
 */
internal interface MLSFaultyKeysConversationsRepairUseCase {
    suspend operator fun invoke()
}

internal class MLSFaultyKeysConversationsRepairUseCaseImpl(
    private val selfUserId: UserId,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val kaliumConfigs: KaliumConfigs,
    private val userConfigRepository: UserConfigRepository,
    private val repairFaultyRemovalKeys: RepairFaultyRemovalKeysUseCase,
    kaliumLogger: KaliumLogger,
) : MLSFaultyKeysConversationsRepairUseCase {
    private val logger by lazy { kaliumLogger.withTextTag("MLSFaultyKeysRepairUseCase") }

    override suspend fun invoke() {
        val wasRepairPerformed = userConfigRepository.isMLSFaultyKeysRepairExecuted()
        if (wasRepairPerformed) {
            logger.d("Skipping MLS faulty keys repair - already executed for current user ${selfUserId.toLogString()}")
            return
        }

        logger.d("Starting MLS faulty keys repair - for current user ${selfUserId.toLogString()}")
        incrementalSyncRepository.incrementalSyncState.filter { it is IncrementalSyncStatus.Live }.collect {
            val matched = kaliumConfigs.domainWithFaultyKeysMap.filter { (domain, _) -> domain.contains(selfUserId.domain) }
            when {
                matched.isEmpty() -> {
                    logger.d("Skipping MLS faulty keys repair - domain does not match current user ${selfUserId.toLogString()}")
                }

                else -> {
                    matched.forEach { (domain, faultyKey) ->
                        val result = repairFaultyRemovalKeys(
                            TargetedRepairParam(
                                domain = domain,
                                faultyKey = faultyKey
                            )
                        )

                        when (result) {
                            RepairResult.Error ->
                                logger.e("Error occurred during MLS faulty keys repair for user ${selfUserId.toLogString()}")

                            RepairResult.NoConversationsToRepair ->
                                logger.d("No conversations to repair for user ${selfUserId.toLogString()}")

                            RepairResult.RepairNotNeeded ->
                                logger.d("Repair not needed for user ${selfUserId.toLogString()}")

                            is RepairResult.RepairPerformed -> {
                                mapResultOfRepairPerformed(result)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun mapResultOfRepairPerformed(result: RepairResult.RepairPerformed) {
        if (result.failedRepairs.isEmpty()) {
            logger.i("Successfully repaired all conversations with faulty MLS keys for user ${selfUserId.toLogString()}")
            userConfigRepository.setMLSFaultyKeysRepairExecuted(true)
        } else {
            logger.w("Failed to repair some conversations [${result.failedRepairs.size}] for user ${selfUserId.toLogString()}")
        }
    }
}
