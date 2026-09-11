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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.cells.domain.model.WireCellsConfig
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.data.featureConfig.CellsInternalConfigModel
import com.wire.kalium.logic.data.featureConfig.CellsInternalModel
import com.wire.kalium.logic.data.featureConfig.CellsModel
import com.wire.kalium.logic.data.featureConfig.CollaboraEdition
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import dev.mokkery.matcher.eq
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class CellsConfigHandlerTest {

    @Test
    fun givenCellsEnabled_whenHandling_thenFeatureIsEnabled() = runTest {
        val (arrangement, handler) = arrange {
            withSetCellsEnabledReturning(Either.Right(Unit))
        }

        val result = handler.handle(CellsModel(Status.ENABLED))

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.setCellsEnabled(true) }
    }

    @Test
    fun givenCellsConfigMissing_whenHandling_thenFeatureIsDisabled() = runTest {
        val (arrangement, handler) = arrange {
            withSetCellsEnabledReturning(Either.Right(Unit))
        }

        val result = handler.handle(null as CellsModel?)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.setCellsEnabled(false) }
    }

    @Test
    fun givenInternalCellsConfig_whenHandling_thenMappedConfigIsStored() = runTest {
        val model = CellsInternalModel(
            status = Status.ENABLED,
            config = CellsInternalConfigModel(
                backendUrl = "https://cells.example.com",
                collaboraEdition = CollaboraEdition.CODE,
                perUserQuotaBytes = 1024L,
            )
        )
        val expected = WireCellsConfig(
            backendUrl = model.config.backendUrl,
            collabora = model.config.collaboraEdition,
            teamQuotaBytes = model.config.perUserQuotaBytes,
        )
        val (arrangement, handler) = arrange {
            withSetWireCellsConfigReturning(Either.Right(Unit))
        }

        val result = handler.handle(model)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.setWireCellsConfig(eq(expected)) }
    }

    @Test
    fun givenInternalCellsConfigMissing_whenHandling_thenStoredConfigIsCleared() = runTest {
        val (arrangement, handler) = arrange {
            withSetWireCellsConfigReturning(Either.Right(Unit))
        }

        val result = handler.handle(null as CellsInternalModel?)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.setWireCellsConfig(eq(null)) }
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit,
    ) : UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl() {

        suspend fun arrange() = run {
            block()
            this@Arrangement to CellsConfigHandler(userConfigRepository)
        }
    }
}
