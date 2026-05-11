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
package com.wire.kalium.logic.feature.mlsmigration

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigTest
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.feature.featureConfig.handler.MLSConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSMigrationConfigHandler
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationWorkerTest.Arrangement.Companion.MIGRATION_CONFIG
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationWorkerTest.Arrangement.Companion.NOT_FOUND_FAILURE
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationWorkerTest.Arrangement.Companion.TEST_FAILURE
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class MLSMigrationWorkerTest {
    @Test
    fun givenGettingMigrationConfigurationFails_whenRunningMigration_workerReturnsNoFailure() = runTest {
        val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(NOT_FOUND_FAILURE).arrange()

        val result = mlsMigrationWorker.runMigration()

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.getMigrationConfiguration() }
        verifySuspend(VerifyMode.not) { arrangement.mlsMigrator.migrateProteusConversations() }
    }

    @Test
    fun givenMigrationIsDisabled_whenRunningMigration_workerReturnsNoFailure() = runTest {
        val (arrangement, mlsMigrationWorker) = Arrangement()
            .withGetMLSMigrationConfigurationsReturns(MIGRATION_CONFIG.copy(status = Status.DISABLED).right()).arrange()

        val result = mlsMigrationWorker.runMigration()

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.getMigrationConfiguration() }
        verifySuspend(VerifyMode.not) { arrangement.mlsMigrator.migrateProteusConversations() }
    }

    @Test
    fun givenMigrationIsEnabledButNotStarted_whenRunningMigration_workerReturnsNoFailure() = runTest {
        val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(
            MIGRATION_CONFIG.copy(startTime = Instant.DISTANT_FUTURE, status = Status.ENABLED).right()
        ).arrange()

        val result = mlsMigrationWorker.runMigration()

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.getMigrationConfiguration() }
        verifySuspend(VerifyMode.not) { arrangement.mlsMigrator.migrateProteusConversations() }
    }

    @Test
    fun givenMigrationIsDisabledButStarted_whenRunningMigration_workerReturnsNoFailure() = runTest {
        val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(
            MIGRATION_CONFIG.copy(startTime = Instant.DISTANT_PAST, status = Status.DISABLED).right()
        ).arrange()

        val result = mlsMigrationWorker.runMigration()

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.getMigrationConfiguration() }
        verifySuspend(VerifyMode.not) { arrangement.mlsMigrator.migrateProteusConversations() }
    }

    @Test
    fun givenMigrationIsEnabledAndStartedAndProteusMigrationFails_whenRunningMigration_thenWorkerShouldFail() = runTest {
        val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(
            MIGRATION_CONFIG.copy(startTime = Instant.DISTANT_PAST, status = Status.ENABLED).right()
        ).withMigrateProteusConversationsReturn(TEST_FAILURE).arrange()

        val result = mlsMigrationWorker.runMigration()

        result.shouldFail()

        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.getMigrationConfiguration() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsMigrator.migrateProteusConversations() }
        verifySuspend(VerifyMode.not) { arrangement.mlsMigrator.finaliseAllProteusConversations() }
        verifySuspend(VerifyMode.not) { arrangement.mlsMigrator.finaliseProteusConversations() }
    }

    @Test
    fun givenProteusMigrationSucceedAndMigrationHasNotEnded_whenRunningMigration_thenWorkerShouldSucceed() = runTest {
        val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(
            MIGRATION_CONFIG.copy(startTime = Instant.DISTANT_PAST, endTime = Instant.DISTANT_FUTURE, status = Status.ENABLED).right()
        ).withMigrateProteusConversationsReturn(Unit.right()).withFinaliseProteusConversations(Unit.right()).arrange()

        val result = mlsMigrationWorker.runMigration()

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.getMigrationConfiguration() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsMigrator.migrateProteusConversations() }
        verifySuspend(VerifyMode.not) { arrangement.mlsMigrator.finaliseAllProteusConversations() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsMigrator.finaliseProteusConversations() }
    }

    @Test
    fun givenProteusMigrationSucceedAndMigrationHasNotEndedAndFinaliseProteusConversationsFails_whenRunningMigration_thenWorkerShouldFail() =
        runTest {
            val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(
                MIGRATION_CONFIG.copy(startTime = Instant.DISTANT_PAST, endTime = Instant.DISTANT_FUTURE, status = Status.ENABLED).right()
            ).withMigrateProteusConversationsReturn(Unit.right()).withFinaliseProteusConversations(TEST_FAILURE).arrange()

            val result = mlsMigrationWorker.runMigration()

            result.shouldFail()

            verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.getMigrationConfiguration() }
            verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsMigrator.migrateProteusConversations() }
            verifySuspend(VerifyMode.not) { arrangement.mlsMigrator.finaliseAllProteusConversations() }
            verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsMigrator.finaliseProteusConversations() }
        }

    @Test
    fun givenProteusMigrationSucceedAndMigrationHasEnded_whenRunningMigration_thenWorkerShouldSucceed() = runTest {
        val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(
            MIGRATION_CONFIG.copy(startTime = Instant.DISTANT_PAST, endTime = Instant.DISTANT_PAST, status = Status.ENABLED).right()
        ).withMigrateProteusConversationsReturn(Unit.right()).withFinaliseAllProteusConversations(Unit.right()).arrange()

        val result = mlsMigrationWorker.runMigration()

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.getMigrationConfiguration() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsMigrator.migrateProteusConversations() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsMigrator.finaliseAllProteusConversations() }
        verifySuspend(VerifyMode.not) { arrangement.mlsMigrator.finaliseProteusConversations() }
    }

    @Test
    fun givenProteusMigrationSucceedAndMigrationHasEndedAndFinaliseAllProteusConversationsFails_whenRunningMigration_thenWorkerShouldFail() =
        runTest {
            val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(
                MIGRATION_CONFIG.copy(startTime = Instant.DISTANT_PAST, endTime = Instant.DISTANT_PAST, status = Status.ENABLED)
                    .right()
            ).withMigrateProteusConversationsReturn(Unit.right()).withFinaliseAllProteusConversations(TEST_FAILURE).arrange()

            val result = mlsMigrationWorker.runMigration()

            result.shouldFail()

            verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.getMigrationConfiguration() }
            verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsMigrator.migrateProteusConversations() }
            verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsMigrator.finaliseAllProteusConversations() }
            verifySuspend(VerifyMode.not) { arrangement.mlsMigrator.finaliseProteusConversations() }
        }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val userConfigRepository: UserConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)
        val featureConfigRepository: FeatureConfigRepository = mock<FeatureConfigRepository>(mode = MockMode.autoUnit)
        val updateSupportedProtocolsAndResolveOneOnOnes = mock<UpdateSupportedProtocolsAndResolveOneOnOnesUseCase>(mode = MockMode.autoUnit)
        val mlsMigrator: MLSMigrator = mock<MLSMigrator>(mode = MockMode.autoUnit)

        val mlsConfigHandler = MLSConfigHandler(
            userConfigRepository,
            updateSupportedProtocolsAndResolveOneOnOnes,
            cryptoTransactionProvider
        )

        val mlsMigrationConfigHandler = MLSMigrationConfigHandler(
            userConfigRepository,
            updateSupportedProtocolsAndResolveOneOnOnes,
            cryptoTransactionProvider
        )

        suspend fun withGetMLSMigrationConfigurationsReturns(result: Either<StorageFailure, MLSMigrationModel>) = apply {
            everySuspend { userConfigRepository.getMigrationConfiguration() } returns result
        }

        suspend fun withMigrateProteusConversationsReturn(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { mlsMigrator.migrateProteusConversations() } returns result
        }

        suspend fun withFinaliseAllProteusConversations(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { mlsMigrator.finaliseAllProteusConversations() } returns result
        }

        suspend fun withFinaliseProteusConversations(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { mlsMigrator.finaliseProteusConversations() } returns result
        }

        init {
            runBlocking {
                everySuspend { featureConfigRepository.getFeatureConfigs() } returns FeatureConfigTest.newModel().right()
                everySuspend { userConfigRepository.setMLSEnabled(any<Boolean>()) } returns Unit.right()
                everySuspend { userConfigRepository.getSupportedProtocols() } returns NOT_FOUND_FAILURE
                everySuspend { userConfigRepository.setDefaultProtocol(any<SupportedProtocol>()) } returns Unit.right()
                everySuspend { userConfigRepository.setSupportedProtocols(any<Set<SupportedProtocol>>()) } returns Unit.right()
                everySuspend { userConfigRepository.setSupportedCipherSuite(any<SupportedCipherSuite>()) } returns Unit.right()
                everySuspend { userConfigRepository.setMigrationConfiguration(any<MLSMigrationModel>()) } returns Unit.right()
            }
        }

        fun arrange() = this to MLSMigrationWorkerImpl(
            userConfigRepository, featureConfigRepository, mlsConfigHandler, mlsMigrationConfigHandler, mlsMigrator
        )

        companion object {
            val TEST_FAILURE = CoreFailure.Unknown(Throwable("Testing!")).left()
            val NOT_FOUND_FAILURE = StorageFailure.DataNotFound.left()
            val MLS_CONFIG = MLSModel(
                defaultProtocol = SupportedProtocol.MLS,
                supportedProtocols = setOf(SupportedProtocol.PROTEUS),
                status = Status.ENABLED,
                supportedCipherSuite = null
            )

            val MIGRATION_CONFIG = MLSMigrationModel(
                startTime = null, endTime = null, status = Status.ENABLED
            )
        }
    }
}
