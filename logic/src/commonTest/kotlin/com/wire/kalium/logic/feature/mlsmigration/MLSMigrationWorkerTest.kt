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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
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
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
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

        verify(arrangement.userConfigRepository).suspendFunction(arrangement.userConfigRepository::getMigrationConfiguration)
            .wasInvoked(once)
        verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::migrateProteusConversations).wasNotInvoked()
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

        verify(arrangement.userConfigRepository).suspendFunction(arrangement.userConfigRepository::getMigrationConfiguration)
            .wasInvoked(once)
        verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::migrateProteusConversations).wasNotInvoked()
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

        verify(arrangement.userConfigRepository).suspendFunction(arrangement.userConfigRepository::getMigrationConfiguration)
            .wasInvoked(once)
        verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::migrateProteusConversations).wasNotInvoked()
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

        verify(arrangement.userConfigRepository).suspendFunction(arrangement.userConfigRepository::getMigrationConfiguration)
            .wasInvoked(once)
        verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::migrateProteusConversations).wasNotInvoked()
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

        verify(arrangement.userConfigRepository).suspendFunction(arrangement.userConfigRepository::getMigrationConfiguration)
            .wasInvoked(once)
        verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::migrateProteusConversations).wasInvoked(once)
        verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::finaliseAllProteusConversations).wasNotInvoked()
        verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::finaliseProteusConversations).wasNotInvoked()
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

        verify(arrangement.userConfigRepository).suspendFunction(arrangement.userConfigRepository::getMigrationConfiguration)
            .wasInvoked(once)
        verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::migrateProteusConversations).wasInvoked(once)
        verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::finaliseAllProteusConversations).wasNotInvoked()
        verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::finaliseProteusConversations).wasInvoked(once)
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

            verify(arrangement.userConfigRepository).suspendFunction(arrangement.userConfigRepository::getMigrationConfiguration)
                .wasInvoked(once)
            verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::migrateProteusConversations).wasInvoked(once)
            verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::finaliseAllProteusConversations).wasNotInvoked()
            verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::finaliseProteusConversations).wasInvoked(once)
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

        verify(arrangement.userConfigRepository).suspendFunction(arrangement.userConfigRepository::getMigrationConfiguration)
            .wasInvoked(once)
        verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::migrateProteusConversations).wasInvoked(once)
        verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::finaliseAllProteusConversations).wasInvoked(once)
        verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::finaliseProteusConversations).wasNotInvoked()
    }

    @Test
    fun givenProteusMigrationSucceedAndMigrationHasEndedAndFinaliseAllProteusConversationsFails_whenRunningMigration_thenWorkerShouldFail() =
        runTest {
            // given
            val (arrangement, mlsMigrationWorker) = Arrangement().withGetMLSMigrationConfigurationsReturns(
                MIGRATION_CONFIG.copy(startTime = Instant.DISTANT_PAST, endTime = Instant.DISTANT_PAST, status = Status.ENABLED).right()
            ).withMigrateProteusConversationsReturn(Unit.right()).withFinaliseAllProteusConversations(TEST_FAILURE).arrange()

            // when
            val result = mlsMigrationWorker.runMigration()

            // then
            result.shouldFail()

            verify(arrangement.userConfigRepository).suspendFunction(arrangement.userConfigRepository::getMigrationConfiguration)
                .wasInvoked(once)
            verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::migrateProteusConversations).wasInvoked(once)
            verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::finaliseAllProteusConversations).wasInvoked(once)
            verify(arrangement.mlsMigrator).suspendFunction(arrangement.mlsMigrator::finaliseProteusConversations).wasNotInvoked()
        }

    private class Arrangement {
        @Mock
        val userConfigRepository: UserConfigRepository = mock(classOf<UserConfigRepository>())

        @Mock
        val featureConfigRepository: FeatureConfigRepository = mock(classOf<FeatureConfigRepository>())

        @Mock
        val updateSupportedProtocolsAndResolveOneOnOnes = mock(classOf<UpdateSupportedProtocolsAndResolveOneOnOnesUseCase>())

        @Mock
        val mlsMigrator: MLSMigrator = mock(classOf<MLSMigrator>())

        val mlsConfigHandler = MLSConfigHandler(userConfigRepository, updateSupportedProtocolsAndResolveOneOnOnes)

        val mlsMigrationConfigHandler = MLSMigrationConfigHandler(userConfigRepository, updateSupportedProtocolsAndResolveOneOnOnes)

        fun withGetMLSMigrationConfigurationsReturns(result: Either<StorageFailure, MLSMigrationModel>) = apply {
            given(userConfigRepository).suspendFunction(userConfigRepository::getMigrationConfiguration).whenInvoked().thenReturn(result)
        }

        fun withMigrateProteusConversationsReturn(result: Either<CoreFailure, Unit>) = apply {
            given(mlsMigrator).suspendFunction(mlsMigrator::migrateProteusConversations).whenInvoked().thenReturn(result)
        }

        fun withFinaliseAllProteusConversations(result: Either<CoreFailure, Unit>) = apply {
            given(mlsMigrator).suspendFunction(mlsMigrator::finaliseAllProteusConversations).whenInvoked().thenReturn(result)
        }

        fun withFinaliseProteusConversations(result: Either<CoreFailure, Unit>) = apply {
            given(mlsMigrator).suspendFunction(mlsMigrator::finaliseProteusConversations).whenInvoked().thenReturn(result)
        }

        init {
            given(featureConfigRepository).suspendFunction(featureConfigRepository::getFeatureConfigs).whenInvoked()
                .thenReturn(FeatureConfigTest.newModel().right())
            given(userConfigRepository).function(userConfigRepository::setMLSEnabled).whenInvokedWith(any<Boolean>())
                .thenReturn(Unit.right())
            given(userConfigRepository).suspendFunction(userConfigRepository::getSupportedProtocols).whenInvoked()
                .thenReturn(NOT_FOUND_FAILURE)
            given(userConfigRepository).function(userConfigRepository::setDefaultProtocol).whenInvokedWith(any<SupportedProtocol>())
                .thenReturn(Unit.right())
            given(userConfigRepository).suspendFunction(userConfigRepository::setSupportedProtocols)
                .whenInvokedWith(any<Set<SupportedProtocol>>()).thenReturn(Unit.right())
            given(userConfigRepository).suspendFunction(userConfigRepository::setSupportedCipherSuite)
                .whenInvokedWith(any<SupportedCipherSuite>()).thenReturn(Unit.right())
            given(userConfigRepository).suspendFunction(userConfigRepository::setMigrationConfiguration)
                .whenInvokedWith(any<MLSMigrationModel>()).thenReturn(Unit.right())
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
