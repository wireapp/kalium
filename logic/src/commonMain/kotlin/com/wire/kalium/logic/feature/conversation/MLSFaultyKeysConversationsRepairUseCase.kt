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

import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.debug.RepairFaultyRemovalKeysUseCase
import com.wire.kalium.logic.feature.debug.RepairResult
import com.wire.kalium.logic.feature.debug.TargetedRepairParam
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.util.string.toHexString

/**
 * Internal use case to repair conversations with faulty MLS keys: known faulty keys per domain, and external senders
 * that are not the backend's current removal key.
 * This is intended to run once for each set of configured faulty keys and backend removal keys.
 */
internal interface MLSFaultyKeysConversationsRepairUseCase {
    suspend operator fun invoke()
}

@Suppress("LongParameterList")
internal class MLSFaultyKeysConversationsRepairUseCaseImpl(
    private val selfUserId: UserId,
    private val syncStateObserver: SyncStateObserver,
    private val kaliumConfigs: KaliumConfigs,
    private val userConfigRepository: UserConfigRepository,
    private val repairFaultyRemovalKeys: RepairFaultyRemovalKeysUseCase,
    private val mlsPublicKeysRepository: MLSPublicKeysRepository,
    kaliumLogger: KaliumLogger,
) : MLSFaultyKeysConversationsRepairUseCase {
    private val logger by lazy { kaliumLogger.withTextTag("MLSFaultyKeysConversationsRepairUseCase") }

    override suspend fun invoke() {
        val faultyKeys = kaliumConfigs.domainWithFaultyKeysMap[selfUserId.domain]
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()

        syncStateObserver.waitUntilLive()
        // Groups whose external sender isn't the current removal key are repaired too, so a key change reruns the repair.
        val removalKeys = mlsPublicKeysRepository.getKeys().getOrElse {
            logger.w("Skipping MLS faulty keys repair - failed to get the removal keys: $it")
            return
        }
        val repairFingerprint = repairFingerprint(selfUserId.domain, faultyKeys, removalKeys)
        if (userConfigRepository.getMLSFaultyKeysRepairFingerprint() == repairFingerprint) {
            logger.d("Skipping MLS faulty keys repair - already executed for current configuration")
            return
        }

        logger.d("Starting MLS faulty keys repair - for current user ${selfUserId.toLogString()}")
        val result = repairFaultyRemovalKeys(
            TargetedRepairParam(
                domain = selfUserId.domain,
                faultyKeys = faultyKeys
            )
        )

        when (result) {
            RepairResult.Error ->
                logger.e("Error occurred during MLS faulty keys repair for user ${selfUserId.toLogString()}")

            RepairResult.NoConversationsToRepair -> {
                userConfigRepository.setMLSFaultyKeysRepairFingerprint(repairFingerprint)
                logger.d("No conversations to repair for user ${selfUserId.toLogString()}")
            }

            RepairResult.RepairNotNeeded ->
                logger.d("Repair not needed for user ${selfUserId.toLogString()}")

            is RepairResult.RepairPerformed -> mapResultOfRepairPerformed(result, repairFingerprint)
        }
    }

    private suspend fun mapResultOfRepairPerformed(result: RepairResult.RepairPerformed, repairFingerprint: String) {
        if (result.failedRepairs.isEmpty()) {
            userConfigRepository.setMLSFaultyKeysRepairFingerprint(repairFingerprint)
        }
        logger.i("Repair was performed for user: ${selfUserId.toLogString()} with result: ${result.toLogString()}")
    }

    private fun repairFingerprint(domain: String, faultyKeys: List<String>, removalKeys: MLSPublicKeys): String {
        val normalizedKeys = faultyKeys.map { it.lowercase() }.sorted().joinToString(",")
        val normalizedRemovalKeys = removalKeys.removal.orEmpty().entries.sortedBy { it.key }.joinToString(",")
        val normalizedConfig = "$domain:$normalizedKeys:$normalizedRemovalKeys"
        return calcSHA256(normalizedConfig.encodeToByteArray()).toHexString()
    }
}
