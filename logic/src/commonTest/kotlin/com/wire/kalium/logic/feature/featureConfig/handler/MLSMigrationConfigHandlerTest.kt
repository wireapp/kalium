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

import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.UpdateSupportedProtocolsAndResolveOneOnOnesArrangement
import com.wire.kalium.logic.util.arrangement.usecase.UpdateSupportedProtocolsAndResolveOneOnOnesArrangementImpl
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
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

        coVerify {
            arrangement.userConfigRepository.setMigrationConfiguration(eq(MIGRATION_CONFIG))
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.updateSupportedProtocolsAndResolveOneOnOnes.invoke(eq(true))
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.updateSupportedProtocolsAndResolveOneOnOnes.invoke(any())
        }.wasNotInvoked()
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl(),
        UpdateSupportedProtocolsAndResolveOneOnOnesArrangement by UpdateSupportedProtocolsAndResolveOneOnOnesArrangementImpl() {
        fun arrange() = run {
            runBlocking { block() }
            this@Arrangement to MLSMigrationConfigHandler(
                userConfigRepository = userConfigRepository,
                updateSupportedProtocolsAndResolveOneOnOnes = updateSupportedProtocolsAndResolveOneOnOnes
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

