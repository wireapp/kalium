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
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.debug.RepairFaultyRemovalKeysUseCase
import com.wire.kalium.logic.feature.debug.RepairResult
import com.wire.kalium.logic.feature.debug.TargetedRepairParam
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.util.string.toHexString
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
    fun givenNoKeysForCurrentDomain_whenInvoking_thenClearFingerprintAndSkipRepair() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDomainWithFaultyKeysMap(emptyMap())
            .withStoredFingerprint("previous-fingerprint")
            .arrange()

        useCase()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setMLSFaultyKeysRepairFingerprint(null)
        }
        verifySuspend(VerifyMode.not) { arrangement.syncStateObserver.waitUntilLive() }
        verifySuspend(VerifyMode.not) { arrangement.repairFaultyRemovalKeysUseCase(any()) }
    }

    @Test
    fun givenCurrentConfigurationWasRepaired_whenInvoking_thenSkipRepair() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDomainWithFaultyKeysMap(mapOf(TestUser.USER_ID.domain to FAULTY_KEYS.reversed()))
            .withStoredFingerprint(repairFingerprint(FAULTY_KEYS))
            .arrange()

        useCase()

        verifySuspend(VerifyMode.not) { arrangement.syncStateObserver.waitUntilLive() }
        verifySuspend(VerifyMode.not) { arrangement.repairFaultyRemovalKeysUseCase(any()) }
        verifySuspend(VerifyMode.not) { arrangement.userConfigRepository.setMLSFaultyKeysRepairFingerprint(any()) }
    }

    @Test
    fun givenLegacyBooleanState_whenInvoking_thenRunRepairAndStoreFingerprint() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDomainWithFaultyKeysMap(mapOf(TestUser.USER_ID.domain to FAULTY_KEYS))
            .withStoredFingerprint("true")
            .withRepairResult(RepairResult.NoConversationsToRepair)
            .arrange()

        useCase()

        verifySuspend(VerifyMode.exactly(1)) { arrangement.syncStateObserver.waitUntilLive() }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setMLSFaultyKeysRepairFingerprint(repairFingerprint(FAULTY_KEYS))
        }
    }

    @Test
    fun givenChangedConfiguration_whenInvoking_thenRunRepairWithCurrentKeys() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDomainWithFaultyKeysMap(
                mapOf(
                    TestUser.USER_ID.domain to FAULTY_KEYS,
                    "other.domain" to listOf("other-key")
                )
            )
            .withStoredFingerprint("previous-fingerprint")
            .arrange()

        useCase()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.repairFaultyRemovalKeysUseCase(
                TargetedRepairParam(
                    domain = TestUser.USER_ID.domain,
                    faultyKeys = FAULTY_KEYS
                )
            )
        }
    }

    @Test
    fun givenSuccessfulRepair_whenInvoking_thenStoreCurrentFingerprint() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDomainWithFaultyKeysMap(mapOf(TestUser.USER_ID.domain to FAULTY_KEYS))
            .withRepairResult(
                RepairResult.RepairPerformed(
                    totalConversationsChecked = 10,
                    conversationsWithFaultyKeys = 5,
                    successfullyRepairedConversations = 5,
                    failedRepairs = emptyList()
                )
            )
            .arrange()

        useCase()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setMLSFaultyKeysRepairFingerprint(repairFingerprint(FAULTY_KEYS))
        }
    }

    @Test
    fun givenRepairHasFailures_whenInvoking_thenDoNotStoreFingerprint() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDomainWithFaultyKeysMap(mapOf(TestUser.USER_ID.domain to FAULTY_KEYS))
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
            arrangement.userConfigRepository.setMLSFaultyKeysRepairFingerprint(any())
        }
    }

    @Test
    fun givenRepairReturnsError_whenInvoking_thenDoNotStoreFingerprint() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDomainWithFaultyKeysMap(mapOf(TestUser.USER_ID.domain to FAULTY_KEYS))
            .withRepairResult(RepairResult.Error)
            .arrange()

        useCase()

        verifySuspend(VerifyMode.not) {
            arrangement.userConfigRepository.setMLSFaultyKeysRepairFingerprint(any())
        }
    }

    private class Arrangement {
        val userConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)
        val syncStateObserver = mock<SyncStateObserver>(mode = MockMode.autoUnit)
        val repairFaultyRemovalKeysUseCase = mock<RepairFaultyRemovalKeysUseCase>(mode = MockMode.autoUnit)
        private var kaliumConfigs = KaliumConfigs()
        private var storedFingerprint: String? = null
        private var repairResult: RepairResult = RepairResult.NoConversationsToRepair
        private var setFingerprintResult: Either<StorageFailure, Unit> = Unit.right()

        fun withStoredFingerprint(fingerprint: String?) = apply {
            storedFingerprint = fingerprint
        }

        fun withDomainWithFaultyKeysMap(map: Map<String, List<String>>) = apply {
            kaliumConfigs = kaliumConfigs.copy(domainWithFaultyKeysMap = map)
        }

        fun withRepairResult(result: RepairResult) = apply {
            repairResult = result
        }

        suspend fun arrange(): Pair<Arrangement, MLSFaultyKeysConversationsRepairUseCaseImpl> {
            everySuspend { userConfigRepository.getMLSFaultyKeysRepairFingerprint() } returns storedFingerprint
            everySuspend { userConfigRepository.setMLSFaultyKeysRepairFingerprint(any()) } returns setFingerprintResult
            everySuspend { syncStateObserver.waitUntilLive() } returns Unit
            everySuspend { repairFaultyRemovalKeysUseCase(any()) } returns repairResult

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

    private companion object {
        val FAULTY_KEYS = listOf(
            "16665373b6bf396f75914a0bed297d44",
            "26665373b6bf396f75914a0bed297d55"
        )

        fun repairFingerprint(faultyKeys: List<String>): String {
            val normalizedKeys = faultyKeys.map { it.lowercase() }.sorted().joinToString(",")
            return calcSHA256("${TestUser.USER_ID.domain}:$normalizedKeys".encodeToByteArray()).toHexString()
        }
    }
}
