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

import app.cash.turbine.test
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.keypackage.MLSMembershipAuditRepository
import com.wire.kalium.logic.data.keypackage.MLSMembershipAuditState
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesResult
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MLSConversationMembershipAuditManagerTest {

    @Test
    fun givenRequiredAudit_whenIncrementalSyncBecomesLive_thenKeyPackageCheckIsRequested() = runTest {
        val (arrangement, manager) = Arrangement()
            .withAuditState(MLSMembershipAuditState.REQUIRED)
            .arrange()

        manager.observeShouldForceKeyPackageCheck().test {
            assertFalse(awaitItem())

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)

            assertTrue(awaitItem())
        }
    }

    @Test
    fun givenRequiredAuditAndLiveSync_whenSlowSyncCompletes_thenKeyPackageCheckIsRequested() = runTest {
        val (arrangement, manager) = Arrangement()
            .withAuditState(MLSMembershipAuditState.REQUIRED)
            .withSlowSyncState(SlowSyncStatus.Pending)
            .withIncrementalSyncState(IncrementalSyncStatus.Live)
            .arrange()

        manager.observeShouldForceKeyPackageCheck().test {
            assertFalse(awaitItem())

            arrangement.slowSyncState.value = SlowSyncStatus.Complete

            assertTrue(awaitItem())
        }
    }

    @Test
    fun givenDeferredAudit_whenPostRegistrationSlowSyncCompletes_thenAuditBecomesRequired() = runTest {
        val (arrangement, manager) = Arrangement()
            .withAuditState(MLSMembershipAuditState.REQUIRED_AFTER_SLOW_SYNC)
            .withIncrementalSyncState(IncrementalSyncStatus.Live)
            .arrange()

        manager.observeShouldForceKeyPackageCheck().test {
            assertFalse(awaitItem())

            arrangement.slowSyncState.value = SlowSyncStatus.Pending
            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)
            arrangement.slowSyncState.value = SlowSyncStatus.Complete
            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)

            assertTrue(awaitItem())
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.auditRepository.markAuditRequired()
            }
        }
    }

    @Test
    fun givenDeferredAuditAfterRestart_whenSyncIsAlreadyComplete_thenAnotherSlowSyncCycleIsRequired() = runTest {
        val (_, manager) = Arrangement()
            .withAuditState(MLSMembershipAuditState.REQUIRED_AFTER_SLOW_SYNC)
            .withIncrementalSyncState(IncrementalSyncStatus.Live)
            .arrange()

        manager.observeShouldForceKeyPackageCheck().test {
            assertFalse(awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun givenAuditIsRequiredAndPackagesAreAvailable_whenAuditing_thenAuditRunsAndMarkerIsCleared() = runTest {
        val (arrangement, manager) = Arrangement()
            .withAuditState(MLSMembershipAuditState.REQUIRED)
            .withIncrementalSyncState(IncrementalSyncStatus.Live)
            .withAuditSuccessful()
            .arrange()

        val result = manager.auditIfNeeded(
            RefillKeyPackagesResult.Success(availableCountBeforeRefill = 10, refilled = false)
        )

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.auditMLSConversationMembership.invoke(any())
            arrangement.auditRepository.clearAuditRequired()
        }
    }

    @Test
    fun givenAuditIsRequiredAndPackagesWereRefilled_whenAuditing_thenAuditRuns() = runTest {
        val (arrangement, manager) = Arrangement()
            .withAuditState(MLSMembershipAuditState.REQUIRED)
            .withIncrementalSyncState(IncrementalSyncStatus.Live)
            .withAuditSuccessful()
            .arrange()

        val result = manager.auditIfNeeded(
            RefillKeyPackagesResult.Success(availableCountBeforeRefill = 0, refilled = true)
        )

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.auditMLSConversationMembership.invoke(any())
        }
    }

    @Test
    fun givenAuditIsRequiredButNoPackagesAreAvailable_whenAuditing_thenAuditIsDeferred() = runTest {
        val (arrangement, manager) = Arrangement()
            .withAuditState(MLSMembershipAuditState.REQUIRED)
            .withIncrementalSyncState(IncrementalSyncStatus.Live)
            .arrange()

        val result = manager.auditIfNeeded(
            RefillKeyPackagesResult.Success(availableCountBeforeRefill = 0, refilled = false)
        )

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.not) {
            arrangement.auditMLSConversationMembership.invoke(any())
            arrangement.auditRepository.clearAuditRequired()
        }
    }

    @Test
    fun givenAuditIsRequiredWhileSlowSyncIsPending_whenAuditing_thenAuditIsDeferred() = runTest {
        val (arrangement, manager) = Arrangement()
            .withAuditState(MLSMembershipAuditState.REQUIRED)
            .withIncrementalSyncState(IncrementalSyncStatus.Live)
            .withSlowSyncState(SlowSyncStatus.Pending)
            .arrange()

        val result = manager.auditIfNeeded(
            RefillKeyPackagesResult.Success(availableCountBeforeRefill = 10, refilled = false)
        )

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.not) {
            arrangement.auditMLSConversationMembership.invoke(any())
            arrangement.auditRepository.clearAuditRequired()
        }
    }

    @Test
    fun givenAuditFails_whenAuditing_thenMarkerIsRetained() = runTest {
        val (arrangement, manager) = Arrangement()
            .withAuditState(MLSMembershipAuditState.REQUIRED)
            .withIncrementalSyncState(IncrementalSyncStatus.Live)
            .withAuditFailed()
            .arrange()

        val result = manager.auditIfNeeded(
            RefillKeyPackagesResult.Success(availableCountBeforeRefill = 0, refilled = true)
        )

        assertIs<Either.Left<CoreFailure>>(result)
        verifySuspend(VerifyMode.not) {
            arrangement.auditRepository.clearAuditRequired()
        }
    }

    @Test
    fun givenMarkerClearFails_whenAuditing_thenFailureIsReturnedAndMarkerRemainsRequired() = runTest {
        val (arrangement, manager) = Arrangement()
            .withAuditState(MLSMembershipAuditState.REQUIRED)
            .withIncrementalSyncState(IncrementalSyncStatus.Live)
            .withAuditSuccessful()
            .withAuditClearFailed()
            .arrange()

        val result = manager.auditIfNeeded(
            RefillKeyPackagesResult.Success(availableCountBeforeRefill = 10, refilled = false)
        )

        assertIs<Either.Left<StorageFailure>>(result)
        assertEquals(MLSMembershipAuditState.REQUIRED, arrangement.auditState.value)
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()
        val slowSyncRepository: SlowSyncRepository = mock()
        val auditRepository: MLSMembershipAuditRepository = mock()
        val auditMLSConversationMembership: AuditMLSConversationMembershipUseCase = mock()
        val slowSyncState = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete)
        val auditState = MutableStateFlow(MLSMembershipAuditState.NOT_REQUIRED)
        private var auditClearFailure: StorageFailure? = null

        fun withAuditState(state: MLSMembershipAuditState) = apply {
            auditState.value = state
        }

        fun withSlowSyncState(state: SlowSyncStatus) = apply {
            slowSyncState.value = state
        }

        suspend fun withIncrementalSyncState(state: IncrementalSyncStatus) = apply {
            incrementalSyncRepository.updateIncrementalSyncState(state)
        }

        suspend fun withAuditSuccessful() = apply {
            everySuspend {
                auditMLSConversationMembership.invoke(any())
            } returns AuditMLSConversationMembershipResult.Success
        }

        suspend fun withAuditFailed() = apply {
            everySuspend {
                auditMLSConversationMembership.invoke(any())
            } returns AuditMLSConversationMembershipResult.Failure(CoreFailure.MissingClientRegistration)
        }

        fun withAuditClearFailed() = apply {
            auditClearFailure = StorageFailure.DataNotFound
        }

        suspend fun arrange(): Pair<Arrangement, MLSConversationMembershipAuditManager> {
            every {
                slowSyncRepository.slowSyncStatus
            } returns slowSyncState
            every {
                auditRepository.observeAuditState()
            } returns auditState
            everySuspend {
                auditRepository.getAuditState()
            } calls { Either.Right(auditState.value) }
            everySuspend {
                auditRepository.markAuditRequired()
            } calls {
                auditState.value = MLSMembershipAuditState.REQUIRED
                Either.Right(Unit)
            }
            everySuspend {
                auditRepository.clearAuditRequired()
            } calls {
                auditClearFailure?.let { Either.Left(it) } ?: run {
                    auditState.value = MLSMembershipAuditState.NOT_REQUIRED
                    Either.Right(Unit)
                }
            }
            withTransactionReturning(Either.Right(Unit))
            return this to MLSConversationMembershipAuditManagerImpl(
                incrementalSyncRepository,
                slowSyncRepository,
                auditRepository,
                lazy { auditMLSConversationMembership },
                cryptoTransactionProvider
            )
        }
    }
}
