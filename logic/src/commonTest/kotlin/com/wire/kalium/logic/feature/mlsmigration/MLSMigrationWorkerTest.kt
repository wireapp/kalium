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
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.of
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class MLSMigrationWorkerTest {
    @Test
    fun givenGettingMigrationConfigurationFails_whenRunningMigration_workerReturnsNoFailure() = runTest {
        // given
        val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(NOT_FOUND_FAILURE).arrange()

        // when
        val result = mlsMigrationWorker.runMigration()

        // then
        result.shouldSucceed()

        coVerify { arrangement.userConfigRepository.getMigrationConfiguration() }.wasInvoked(once)
        coVerify { arrangement.mlsMigrator.migrateProteusConversations() }.wasNotInvoked()
    }

    @Test
    fun givenMigrationIsDisabled_whenRunningMigration_workerReturnsNoFailure() = runTest {
        // given
        val (arrangement, mlsMigrationWorker) = Arrangement()
            .withGetMLSMigrationConfigurationsReturns(MIGRATION_CONFIG.copy(status = Status.DISABLED).right()).arrange()

        // when
        val result = mlsMigrationWorker.runMigration()

        // then
        result.shouldSucceed()

        coVerify { arrangement.userConfigRepository.getMigrationConfiguration() }.wasInvoked(once)
        coVerify { arrangement.mlsMigrator.migrateProteusConversations() }.wasNotInvoked()
    }

    @Test
    fun givenMigrationIsEnabledButNotStarted_whenRunningMigration_workerReturnsNoFailure() = runTest {
        // given
        val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(
            MIGRATION_CONFIG.copy(startTime = Instant.DISTANT_FUTURE, status = Status.ENABLED).right()
        ).arrange()

        // when
        val result = mlsMigrationWorker.runMigration()

        // then
        result.shouldSucceed()

        coVerify { arrangement.userConfigRepository.getMigrationConfiguration() }.wasInvoked(once)
        coVerify { arrangement.mlsMigrator.migrateProteusConversations() }.wasNotInvoked()
    }

    @Test
    fun givenMigrationIsDisabledButStarted_whenRunningMigration_workerReturnsNoFailure() = runTest {
        // given
        val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(
            MIGRATION_CONFIG.copy(startTime = Instant.DISTANT_PAST, status = Status.DISABLED).right()
        ).arrange()

        // when
        val result = mlsMigrationWorker.runMigration()

        // then
        result.shouldSucceed()

        coVerify { arrangement.userConfigRepository.getMigrationConfiguration() }.wasInvoked(once)
        coVerify { arrangement.mlsMigrator.migrateProteusConversations() }.wasNotInvoked()
    }

    @Test
    fun givenMigrationIsEnabledAndStartedAndProteusMigrationFails_whenRunningMigration_thenWorkerShouldFail() = runTest {
        // given
        val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(
            MIGRATION_CONFIG.copy(startTime = Instant.DISTANT_PAST, status = Status.ENABLED).right()
        ).withMigrateProteusConversationsReturn(TEST_FAILURE).arrange()

        // when
        val result = mlsMigrationWorker.runMigration()

        // then
        result.shouldFail()

        coVerify { arrangement.userConfigRepository.getMigrationConfiguration() }.wasInvoked(once)
        coVerify { arrangement.mlsMigrator.migrateProteusConversations() }.wasInvoked(once)
        coVerify { arrangement.mlsMigrator.finaliseAllProteusConversations() }.wasNotInvoked()
        coVerify { arrangement.mlsMigrator.finaliseProteusConversations() }.wasNotInvoked()
    }

    @Test
    fun givenProteusMigrationSucceedAndMigrationHasNotEnded_whenRunningMigration_thenWorkerShouldSucceed() = runTest {
        // given
        val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(
            MIGRATION_CONFIG.copy(startTime = Instant.DISTANT_PAST, endTime = Instant.DISTANT_FUTURE, status = Status.ENABLED).right()
        ).withMigrateProteusConversationsReturn(Unit.right()).withFinaliseProteusConversations(Unit.right()).arrange()

        // when
        val result = mlsMigrationWorker.runMigration()

        // then
        result.shouldSucceed()

        coVerify { arrangement.userConfigRepository.getMigrationConfiguration() }.wasInvoked(once)
        coVerify { arrangement.mlsMigrator.migrateProteusConversations() }.wasInvoked(once)
        coVerify { arrangement.mlsMigrator.finaliseAllProteusConversations() }.wasNotInvoked()
        coVerify { arrangement.mlsMigrator.finaliseProteusConversations() }.wasInvoked(once)
    }

    @Test
    fun givenProteusMigrationSucceedAndMigrationHasNotEndedAndFinaliseProteusConversationsFails_whenRunningMigration_thenWorkerShouldFail() =
        runTest {
            // given
            val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(
                MIGRATION_CONFIG.copy(startTime = Instant.DISTANT_PAST, endTime = Instant.DISTANT_FUTURE, status = Status.ENABLED).right()
            ).withMigrateProteusConversationsReturn(Unit.right()).withFinaliseProteusConversations(TEST_FAILURE).arrange()

            // when
            val result = mlsMigrationWorker.runMigration()

            // then
            result.shouldFail()

            coVerify { arrangement.userConfigRepository.getMigrationConfiguration() }.wasInvoked(once)
            coVerify { arrangement.mlsMigrator.migrateProteusConversations() }.wasInvoked(once)
            coVerify { arrangement.mlsMigrator.finaliseAllProteusConversations() }.wasNotInvoked()
            coVerify { arrangement.mlsMigrator.finaliseProteusConversations() }.wasInvoked(once)
        }

    @Test
    fun givenProteusMigrationSucceedAndMigrationHasEnded_whenRunningMigration_thenWorkerShouldSucceed() = runTest {
        // given
        val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(
            MIGRATION_CONFIG.copy(startTime = Instant.DISTANT_PAST, endTime = Instant.DISTANT_PAST, status = Status.ENABLED).right()
        ).withMigrateProteusConversationsReturn(Unit.right()).withFinaliseAllProteusConversations(Unit.right()).arrange()

        // when
        val result = mlsMigrationWorker.runMigration()

        // then
        result.shouldSucceed()

        coVerify { arrangement.userConfigRepository.getMigrationConfiguration() }.wasInvoked(once)
        coVerify { arrangement.mlsMigrator.migrateProteusConversations() }.wasInvoked(once)
        coVerify { arrangement.mlsMigrator.finaliseAllProteusConversations() }.wasInvoked(once)
        coVerify { arrangement.mlsMigrator.finaliseProteusConversations() }.wasNotInvoked()
    }

    @Test
    fun givenProteusMigrationSucceedAndMigrationHasEndedAndFinaliseAllProteusConversationsFails_whenRunningMigration_thenWorkerShouldFail() =
        runTest {
            // given
            val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(
                MIGRATION_CONFIG.copy(startTime = Instant.DISTANT_PAST, endTime = Instant.DISTANT_PAST, status = Status.ENABLED)
                    .right()
            ).withMigrateProteusConversationsReturn(Unit.right()).withFinaliseAllProteusConversations(TEST_FAILURE).arrange()

            // when
            val result = mlsMigrationWorker.runMigration()

            // then
            result.shouldFail()

            coVerify { arrangement.userConfigRepository.getMigrationConfiguration() }.wasInvoked(once)
            coVerify { arrangement.mlsMigrator.migrateProteusConversations() }.wasInvoked(once)
            coVerify { arrangement.mlsMigrator.finaliseAllProteusConversations() }.wasInvoked(once)
            coVerify { arrangement.mlsMigrator.finaliseProteusConversations() }.wasNotInvoked()
        }

    private class Arrangement {
                val userConfigRepository: UserConfigRepository = mock(of<UserConfigRepository>())
        val featureConfigRepository: FeatureConfigRepository = mock(of<FeatureConfigRepository>())
        val updateSupportedProtocolsAndResolveOneOnOnes = mock(of<UpdateSupportedProtocolsAndResolveOneOnOnesUseCase>())
        val mlsMigrator: MLSMigrator = mock(of<MLSMigrator>())

        val mlsConfigHandler = MLSConfigHandler(userConfigRepository, updateSupportedProtocolsAndResolveOneOnOnes)

        val mlsMigrationConfigHandler = MLSMigrationConfigHandler(userConfigRepository, updateSupportedProtocolsAndResolveOneOnOnes)

        suspend fun withGetMLSMigrationConfigurationsReturns(result: Either<StorageFailure, MLSMigrationModel>) = apply {
            coEvery { userConfigRepository.getMigrationConfiguration() }.returns(result)
        }

        suspend fun withMigrateProteusConversationsReturn(result: Either<CoreFailure, Unit>) = apply {
            coEvery { mlsMigrator.migrateProteusConversations() }.returns(result)
        }

        suspend fun withFinaliseAllProteusConversations(result: Either<CoreFailure, Unit>) = apply {
            coEvery { mlsMigrator.finaliseAllProteusConversations() }.returns(result)
        }

        suspend fun withFinaliseProteusConversations(result: Either<CoreFailure, Unit>) = apply {
            coEvery { mlsMigrator.finaliseProteusConversations() }.returns(result)
        }

        init {
            runBlocking {
                coEvery { featureConfigRepository.getFeatureConfigs() }.returns(FeatureConfigTest.newModel().right())
                every { userConfigRepository.setMLSEnabled(any<Boolean>()) }.returns(Unit.right())
                coEvery { userConfigRepository.getSupportedProtocols() }.returns(NOT_FOUND_FAILURE)
                every { userConfigRepository.setDefaultProtocol(any<SupportedProtocol>()) }.returns(Unit.right())
                coEvery { userConfigRepository.setSupportedProtocols(any<Set<SupportedProtocol>>()) }.returns(Unit.right())
                coEvery { userConfigRepository.setSupportedCipherSuite(any<SupportedCipherSuite>()) }.returns(Unit.right())
                coEvery { userConfigRepository.setMigrationConfiguration(any<MLSMigrationModel>()) }.returns(Unit.right())
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
