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
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any as mokkeryAny
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
            arrangement.userConfigRepository.setMigrationConfiguration(MIGRATION_CONFIG)
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
            arrangement.updateSupportedProtocolsAndResolveOneOnOnes(mokkeryAny(), true)
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

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.updateSupportedProtocolsAndResolveOneOnOnes(mokkeryAny(), mokkeryAny())
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
            arrangement.updateSupportedProtocolsAndResolveOneOnOnes(arrangement.transactionContext, true)
        }
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.cryptoTransactionProvider.transaction<Any>(mokkeryAny(), mokkeryAny())
        }
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val userConfigRepository = mock<UserConfigRepository>()
        val updateSupportedProtocolsAndResolveOneOnOnes = mock<UpdateSupportedProtocolsAndResolveOneOnOnesUseCase>()

        suspend fun withSetMigrationConfigurationSuccessful() = apply {
            everySuspend {
                userConfigRepository.setMigrationConfiguration(mokkeryAny())
            } returns Either.Right(Unit)
        }

        suspend fun withUpdateSupportedProtocolsAndResolveOneOnOnesSuccessful() = apply {
            everySuspend {
                updateSupportedProtocolsAndResolveOneOnOnes(mokkeryAny(), mokkeryAny())
            } returns Either.Right(Unit)
        }

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
