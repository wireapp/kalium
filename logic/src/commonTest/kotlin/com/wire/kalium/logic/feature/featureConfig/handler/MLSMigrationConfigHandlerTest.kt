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
package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class MLSMigrationConfigHandlerTest {
    @Test
    fun givenMlsConfiguration_whenHandling_thenSetMlsConfiguration() = runTest {
        val (arrangement, handler) = arrange {
            withSetMigrationConfigurationSuccessful()
        }

        handler.handle(MIGRATION_CONFIG, duringSlowSync = false)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setMigrationConfiguration(eq(MIGRATION_CONFIG))
        }
    }

    @Test
    fun givenMigrationHasEnded_whenHandling_thenUpdateSelfSupportedProtocols() = runTest {
        val (arrangement, handler) = arrange {
            withUpdateSupportedProtocolsAndResolveOneOnOnesSuccessful()
            withSetMigrationConfigurationSuccessful()
        }

        handler.handle(
            MIGRATION_CONFIG.copy(
                startTime = Instant.DISTANT_PAST,
                endTime = Instant.DISTANT_PAST
            ), duringSlowSync = false
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateSupportedProtocolsAndResolveOneOnOnes.invoke(any(), eq(true))
        }
    }

    @Test
    fun givenMigrationHasEndedDuringSlowSync_whenHandling_thenDontUpdateSelfSupportedProtocols() = runTest {
        val (arrangement, handler) = arrange {
            withUpdateSupportedProtocolsAndResolveOneOnOnesSuccessful()
            withSetMigrationConfigurationSuccessful()
        }

        handler.handle(
            MIGRATION_CONFIG.copy(
                startTime = Instant.DISTANT_PAST,
                endTime = Instant.DISTANT_PAST
            ), duringSlowSync = true
        )

        verifySuspend(VerifyMode.not) {
            arrangement.updateSupportedProtocolsAndResolveOneOnOnes.invoke(any(), any())
        }
    }

    @Test
    fun givenMigrationHasEndedWithTransactionContext_whenHandling_thenUpdateProtocolsDirectlyWithoutNewTransaction() = runTest {
        val (arrangement, handler) = arrange {
            withUpdateSupportedProtocolsAndResolveOneOnOnesSuccessful()
            withSetMigrationConfigurationSuccessful()
        }

        handler.handle(
            MIGRATION_CONFIG.copy(
                startTime = Instant.DISTANT_PAST,
                endTime = Instant.DISTANT_PAST
            ),
            duringSlowSync = false,
            transactionContext = arrangement.transactionContext
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateSupportedProtocolsAndResolveOneOnOnes.invoke(eq(arrangement.transactionContext), eq(true))
        }
        verifySuspend(VerifyMode.not) {
            arrangement.cryptoTransactionProvider.transaction<Any>(any(), any())
        }
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val userConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)
        val updateSupportedProtocolsAndResolveOneOnOnes = mock<UpdateSupportedProtocolsAndResolveOneOnOnesUseCase>(mode = MockMode.autoUnit)

        fun arrange() = run {
            runBlocking {
                withTransactionReturning(Either.Right(Unit))
                block()
            }
            this@Arrangement to MLSMigrationConfigHandler(
                userConfigRepository = userConfigRepository,
                updateSupportedProtocolsAndResolveOneOnOnes = updateSupportedProtocolsAndResolveOneOnOnes,
                transactionProvider = cryptoTransactionProvider
            )
        }

        suspend fun withSetMigrationConfigurationSuccessful() {
            everySuspend {
                userConfigRepository.setMigrationConfiguration(any())
            } returns Either.Right(Unit)
        }

        suspend fun withUpdateSupportedProtocolsAndResolveOneOnOnesSuccessful() {
            everySuspend {
                updateSupportedProtocolsAndResolveOneOnOnes.invoke(any(), any())
            } returns Either.Right(Unit)
        }
    }

    private companion object {
        fun arrange(configuration: suspend Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        val MIGRATION_CONFIG = MLSMigrationModel(
            startTime = null,
            endTime = null,
            status = Status.ENABLED
        )
    }

}
