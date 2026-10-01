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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.keypackage.MLSMembershipAuditRepository
import com.wire.kalium.logic.data.keypackage.MLSMembershipAuditState
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

internal interface MLSConversationMembershipAuditManager {
    fun observeShouldForceKeyPackageCheck(): Flow<Boolean>

    suspend fun auditIfNeeded(
        refillResult: RefillKeyPackagesResult.Success
    ): Either<CoreFailure, Unit>
}

internal class MLSConversationMembershipAuditManagerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val slowSyncRepository: SlowSyncRepository,
    private val auditRepository: MLSMembershipAuditRepository,
    private val auditMLSConversationMembership: Lazy<AuditMLSConversationMembershipUseCase>,
    private val transactionProvider: CryptoTransactionProvider,
) : MLSConversationMembershipAuditManager {

    override fun observeShouldForceKeyPackageCheck(): Flow<Boolean> =
        combine(
            incrementalSyncRepository.incrementalSyncState,
            slowSyncRepository.slowSyncStatus,
            slowSyncRepository.observeLastSlowSyncCompletionInstant(),
            auditRepository.observeAuditState()
        ) { incrementalSyncState, slowSyncState, lastSlowSyncInstant, auditState ->
            isAuditDue(
                auditState = auditState,
                incrementalSyncState = incrementalSyncState,
                slowSyncState = slowSyncState,
                lastSlowSyncInstant = lastSlowSyncInstant
            )
        }.distinctUntilChanged()

    override suspend fun auditIfNeeded(
        refillResult: RefillKeyPackagesResult.Success
    ): Either<CoreFailure, Unit> =
        auditRepository.getAuditState().flatMap { auditState ->
            val keyPackagesAreAvailable = refillResult.refilled || refillResult.availableCountBeforeRefill > 0

            val shouldRunAudit = keyPackagesAreAvailable && isAuditDue(
                auditState = auditState,
                incrementalSyncState = incrementalSyncRepository.incrementalSyncState.first(),
                slowSyncState = slowSyncRepository.slowSyncStatus.value,
                lastSlowSyncInstant = slowSyncRepository.getLastSlowSyncCompletionInstant()
            )

            if (shouldRunAudit) {
                runAudit()
            } else {
                Either.Right(Unit)
            }
        }

    /**
     * Pure predicate; no state, no side effects, safe under any number of collectors.
     *
     * [MLSMembershipAuditState.REQUIRED_AFTER_SLOW_SYNC] additionally requires a persisted slow-sync
     * completion instant. [com.wire.kalium.logic.feature.client.RegisterMLSClientUseCase] clears that
     * instant immediately before writing the deferred marker, so any instant observed afterwards
     * necessarily belongs to a slow sync that completed after the MLS client was registered. Unlike
     * [SlowSyncStatus], the instant survives process death, so a restart cannot mistake the in-memory
     * Pending -> Complete skip transition for a real sync.
     */
    private fun isAuditDue(
        auditState: MLSMembershipAuditState,
        incrementalSyncState: IncrementalSyncStatus,
        slowSyncState: SlowSyncStatus,
        lastSlowSyncInstant: Instant?,
    ): Boolean = when (auditState) {
        MLSMembershipAuditState.NOT_REQUIRED -> false
        MLSMembershipAuditState.REQUIRED -> isSyncReadyForAudit(incrementalSyncState, slowSyncState)
        MLSMembershipAuditState.REQUIRED_AFTER_SLOW_SYNC ->
            lastSlowSyncInstant != null && isSyncReadyForAudit(incrementalSyncState, slowSyncState)
    }

    private fun isSyncReadyForAudit(
        incrementalSyncState: IncrementalSyncStatus,
        slowSyncState: SlowSyncStatus,
    ): Boolean =
        incrementalSyncState is IncrementalSyncStatus.Live &&
            slowSyncState is SlowSyncStatus.Complete

    private suspend fun runAudit(): Either<CoreFailure, Unit> {
        kaliumLogger.i("Auditing MLS conversation membership after key package recovery")
        return transactionProvider.transaction("KeyPackageMembershipAudit") { transactionContext ->
            when (val result = auditMLSConversationMembership.value(transactionContext)) {
                AuditMLSConversationMembershipResult.Success -> Either.Right(Unit)
                is AuditMLSConversationMembershipResult.Failure -> Either.Left(result.failure)
            }
        }.flatMap {
            auditRepository.clearAuditRequired()
        }
    }
}
