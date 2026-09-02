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
import com.wire.kalium.common.functional.onFailure
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
import kotlinx.coroutines.flow.map

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

    private var waitingForPostRegistrationSync = false
    private var observedPostRegistrationSlowSync = false

    override fun observeShouldForceKeyPackageCheck(): Flow<Boolean> =
        combine(
            incrementalSyncRepository.incrementalSyncState,
            slowSyncRepository.slowSyncStatus,
            auditRepository.observeAuditState()
        ) { incrementalSyncState, slowSyncState, auditState ->
            Triple(incrementalSyncState, slowSyncState, auditState)
        }.map { (incrementalSyncState, slowSyncState, auditState) ->
            when (auditState) {
                MLSMembershipAuditState.NOT_REQUIRED -> {
                    resetPostRegistrationSyncObservation()
                    false
                }

                MLSMembershipAuditState.REQUIRED -> {
                    resetPostRegistrationSyncObservation()
                    isSyncReadyForAudit(incrementalSyncState, slowSyncState)
                }

                MLSMembershipAuditState.REQUIRED_AFTER_SLOW_SYNC -> {
                    handleAuditWaitingForSlowSync(incrementalSyncState, slowSyncState)
                    false
                }
            }
        }.distinctUntilChanged()

    override suspend fun auditIfNeeded(
        refillResult: RefillKeyPackagesResult.Success
    ): Either<CoreFailure, Unit> =
        auditRepository.getAuditState().flatMap { auditState ->

            val keyPackagesAreAvailable = refillResult.refilled || refillResult.availableCountBeforeRefill > 0

            val shouldRunAudit = when {
                auditState != MLSMembershipAuditState.REQUIRED -> false
                !keyPackagesAreAvailable -> false
                else -> isCurrentSyncReadyForAudit()
            }

            if (shouldRunAudit) {
                runAudit()
            } else {
                Either.Right(Unit)
            }
        }

    private suspend fun handleAuditWaitingForSlowSync(
        incrementalSyncState: IncrementalSyncStatus,
        slowSyncState: SlowSyncStatus,
    ) {
        if (!waitingForPostRegistrationSync) {
            waitingForPostRegistrationSync = true
            observedPostRegistrationSlowSync = slowSyncState !is SlowSyncStatus.Complete
            kaliumLogger.d("Deferring post-registration MLS membership audit until the next completed sync")
            return
        }

        if (slowSyncState !is SlowSyncStatus.Complete) {
            observedPostRegistrationSlowSync = true
        } else if (
            observedPostRegistrationSlowSync &&
            incrementalSyncState is IncrementalSyncStatus.Live
        ) {
            kaliumLogger.i("Post-registration sync completed; MLS membership audit is ready")
            auditRepository.markAuditRequired()
                .onFailure { kaliumLogger.w("Failed to make post-registration MLS membership audit ready: $it") }
        }
    }

    private fun resetPostRegistrationSyncObservation() {
        waitingForPostRegistrationSync = false
        observedPostRegistrationSlowSync = false
    }

    private fun isSyncReadyForAudit(
        incrementalSyncState: IncrementalSyncStatus,
        slowSyncState: SlowSyncStatus,
    ): Boolean =
        incrementalSyncState is IncrementalSyncStatus.Live &&
            slowSyncState is SlowSyncStatus.Complete

    private suspend fun isCurrentSyncReadyForAudit(): Boolean =
        isSyncReadyForAudit(
            incrementalSyncRepository.incrementalSyncState.first(),
            slowSyncRepository.slowSyncStatus.value
        )

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
