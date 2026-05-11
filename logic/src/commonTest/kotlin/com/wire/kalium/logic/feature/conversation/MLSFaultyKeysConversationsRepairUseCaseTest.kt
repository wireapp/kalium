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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.debug.RepairFaultyRemovalKeysUseCase
import com.wire.kalium.logic.feature.debug.RepairResult
import com.wire.kalium.logic.feature.debug.TargetedRepairParam
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncStateObserver
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MLSFaultyKeysConversationsRepairUseCaseTest {

    @Test
    fun givenRepairAlreadyExecuted_whenInvoking_thenSkipRepairAndDoNotWaitForSync() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRepairAlreadyExecuted(true)
            .arrange()

        useCase()

        verifySuspend(VerifyMode.not) {
            arrangement.syncStateObserver.waitUntilLive()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.repairFaultyRemovalKeysUseCase(any())
        }
    }

    @Test
    fun givenRepairNotExecutedYet_whenInvoking_thenWaitForSyncLive() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRepairAlreadyExecuted(false)
            .withDomainWithFaultyKeysMap(emptyMap())
            .arrange()

        useCase()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.syncStateObserver.waitUntilLive()
        }
    }

    @Test
    fun givenDomainDoesNotMatch_whenInvoking_thenDoNotAttemptRepair() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRepairAlreadyExecuted(false)
            .withDomainWithFaultyKeysMap(
                mapOf("other.domain" to listOf("somekey"))
            )
            .arrange()

        useCase()

        verifySuspend(VerifyMode.not) {
            arrangement.repairFaultyRemovalKeysUseCase(any())
        }
    }

    @Test
    fun givenDomainMatches_whenInvoking_thenAttemptRepairWithCorrectParams() = runTest {
        val faultyKey = listOf("16665373b6bf396f75914a0bed297d44")
        val (arrangement, useCase) = Arrangement()
            .withRepairAlreadyExecuted(false)
            .withDomainWithFaultyKeysMap(
                mapOf(TestUser.USER_ID.domain to faultyKey)
            )
            .withRepairResult(RepairResult.NoConversationsToRepair)
            .arrange()

        useCase()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.repairFaultyRemovalKeysUseCase(
                TargetedRepairParam(
                    domain = TestUser.USER_ID.domain,
                    faultyKeys = faultyKey
                )
            )
        }
    }

    @Test
    fun givenSuccessfulRepairWithNoFailures_whenInvoking_thenSetRepairExecutedFlag() = runTest {
        val faultyKey = listOf("16665373b6bf396f75914a0bed297d44")
        val (arrangement, useCase) = Arrangement()
            .withRepairAlreadyExecuted(false)
            .withDomainWithFaultyKeysMap(
                mapOf(TestUser.USER_ID.domain to faultyKey)
            )
            .withRepairResult(
                RepairResult.RepairPerformed(
                    totalConversationsChecked = 10,
                    conversationsWithFaultyKeys = 5,
                    successfullyRepairedConversations = 5,
                    failedRepairs = emptyList()
                )
            )
            .withSetRepairExecutedResult(Unit.right())
            .arrange()

        useCase()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setMLSFaultyKeysRepairExecuted(true)
        }
    }

    @Test
    fun givenRepairWithFailures_whenInvoking_thenDoNotSetRepairExecutedFlag() = runTest {
        val faultyKey = listOf("16665373b6bf396f75914a0bed297d44")
        val (arrangement, useCase) = Arrangement()
            .withRepairAlreadyExecuted(false)
            .withDomainWithFaultyKeysMap(
                mapOf(TestUser.USER_ID.domain to faultyKey)
            )
            .withRepairResult(
                RepairResult.RepairPerformed(
                    totalConversationsChecked = 10,
                    conversationsWithFaultyKeys = 5,
                    successfullyRepairedConversations = 3,
                    failedRepairs = listOf("conv1", "conv2")
                )
            )
            .arrange()

        useCase()

        verifySuspend(VerifyMode.not) {
            arrangement.userConfigRepository.setMLSFaultyKeysRepairExecuted(any())
        }
    }

    @Test
    fun givenRepairReturnsError_whenInvoking_thenDoNotSetRepairExecutedFlag() = runTest {
        val faultyKey = listOf("16665373b6bf396f75914a0bed297d44")
        val (arrangement, useCase) = Arrangement()
            .withRepairAlreadyExecuted(false)
            .withDomainWithFaultyKeysMap(
                mapOf(TestUser.USER_ID.domain to faultyKey)
            )
            .withRepairResult(RepairResult.Error)
            .arrange()

        useCase()

        verifySuspend(VerifyMode.not) {
            arrangement.userConfigRepository.setMLSFaultyKeysRepairExecuted(any())
        }
    }

    @Test
    fun givenRepairReturnsNoConversationsToRepair_whenInvoking_thenDoNotSetRepairExecutedFlag() = runTest {
        val faultyKey = listOf("16665373b6bf396f75914a0bed297d44")
        val (arrangement, useCase) = Arrangement()
            .withRepairAlreadyExecuted(false)
            .withDomainWithFaultyKeysMap(
                mapOf(TestUser.USER_ID.domain to faultyKey)
            )
            .withRepairResult(RepairResult.NoConversationsToRepair)
            .arrange()

        useCase()

        verifySuspend(VerifyMode.not) {
            arrangement.userConfigRepository.setMLSFaultyKeysRepairExecuted(any())
        }
    }

    @Test
    fun givenMultipleDomainsInConfig_whenOnlyOneMatches_thenRepairOnlyMatchingDomain() = runTest {
        val matchingKey = listOf("key1")
        val nonMatchingKey = listOf("key2")
        val (arrangement, useCase) = Arrangement()
            .withRepairAlreadyExecuted(false)
            .withDomainWithFaultyKeysMap(
                mapOf(
                    TestUser.USER_ID.domain to matchingKey,
                    "other.domain" to nonMatchingKey
                )
            )
            .withRepairResult(RepairResult.NoConversationsToRepair)
            .arrange()

        useCase()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.repairFaultyRemovalKeysUseCase(
                TargetedRepairParam(
                    domain = TestUser.USER_ID.domain,
                    faultyKeys = matchingKey
                )
            )
        }
    }

    @Test
    fun givenSetFlagReturnsFailure_whenInvoking_thenOperationCompletes() = runTest {
        val faultyKey = listOf("16665373b6bf396f75914a0bed297d44")
        val (arrangement, useCase) = Arrangement()
            .withRepairAlreadyExecuted(false)
            .withDomainWithFaultyKeysMap(
                mapOf(TestUser.USER_ID.domain to faultyKey)
            )
            .withRepairResult(
                RepairResult.RepairPerformed(
                    totalConversationsChecked = 10,
                    conversationsWithFaultyKeys = 5,
                    successfullyRepairedConversations = 5,
                    failedRepairs = emptyList()
                )
            )
            .withSetRepairExecutedResult(StorageFailure.Generic(RuntimeException("DB error")).left())
            .arrange()

        useCase()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setMLSFaultyKeysRepairExecuted(true)
        }
    }

    private class Arrangement {
        val userConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)
        val syncStateObserver = mock<SyncStateObserver>(mode = MockMode.autoUnit)
        val repairFaultyRemovalKeysUseCase = mock<RepairFaultyRemovalKeysUseCase>(mode = MockMode.autoUnit)
        private var kaliumConfigs = KaliumConfigs()

        suspend fun withRepairAlreadyExecuted(executed: Boolean) = apply {
            everySuspend {
                userConfigRepository.isMLSFaultyKeysRepairExecuted()
            } returns executed
        }

        fun withDomainWithFaultyKeysMap(map: Map<String, List<String>>) = apply {
            kaliumConfigs = kaliumConfigs.copy(domainWithFaultyKeysMap = map)
        }

        suspend fun withRepairResult(result: RepairResult) = apply {
            everySuspend {
                repairFaultyRemovalKeysUseCase(any())
            } returns result
        }

        suspend fun withSetRepairExecutedResult(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                userConfigRepository.setMLSFaultyKeysRepairExecuted(any())
            } returns result
        }

        suspend fun arrange(): Pair<Arrangement, MLSFaultyKeysConversationsRepairUseCaseImpl> {
            everySuspend {
                syncStateObserver.waitUntilLive()
            } returns Unit

            return this to MLSFaultyKeysConversationsRepairUseCaseImpl(
                selfUserId = TestUser.USER_ID,
                syncStateObserver = syncStateObserver,
                kaliumConfigs = kaliumConfigs,
                userConfigRepository = userConfigRepository,
                repairFaultyRemovalKeys = repairFaultyRemovalKeysUseCase,
                kaliumLogger = kaliumLogger
            )
        }
    }
}
