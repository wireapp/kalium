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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.CellsConfigPersistence
import com.wire.kalium.logic.data.featureConfig.CellsInternalConfigModel
import com.wire.kalium.logic.data.featureConfig.CellsInternalModel
import com.wire.kalium.logic.data.featureConfig.CellsModel
import com.wire.kalium.logic.data.featureConfig.CollaboraEdition
import com.wire.kalium.logic.data.featureConfig.Status
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CellsConfigHandlerTest {

    @Test
    fun givenEnabledCellsModel_whenHandled_thenCellsAreEnabled() = runTest {
        val persistence = RecordingCellsConfigPersistence()
        val handler = CellsConfigHandler(persistence)

        val result = handler.handle(CellsModel(Status.ENABLED))

        assertEquals(Either.Right(Unit), result)
        assertEquals(listOf(true), persistence.enabledCalls)
    }

    @Test
    fun givenNullCellsModel_whenHandled_thenCellsAreDisabled() = runTest {
        val persistence = RecordingCellsConfigPersistence()
        val handler = CellsConfigHandler(persistence)

        val result = handler.handle(null as CellsModel?)

        assertEquals(Either.Right(Unit), result)
        assertEquals(listOf(false), persistence.enabledCalls)
    }

    @Test
    fun givenInternalCellsModel_whenHandled_thenOriginalConfigIsPersisted() = runTest {
        val persistence = RecordingCellsConfigPersistence()
        val handler = CellsConfigHandler(persistence)
        val config = CellsInternalConfigModel(
            backendUrl = "https://cells.example.com",
            collaboraEdition = CollaboraEdition.COOL,
            perUserQuotaBytes = 42L,
        )

        val result = handler.handle(CellsInternalModel(Status.DISABLED, config))

        assertEquals(Either.Right(Unit), result)
        assertSame(config, persistence.internalConfigCalls.single())
    }

    @Test
    fun givenNullInternalCellsModel_whenHandled_thenNullConfigIsPersisted() = runTest {
        val persistence = RecordingCellsConfigPersistence()
        val handler = CellsConfigHandler(persistence)

        val result = handler.handle(null as CellsInternalModel?)

        assertEquals(Either.Right(Unit), result)
        assertEquals(listOf<CellsInternalConfigModel?>(null), persistence.internalConfigCalls)
    }

    @Test
    fun givenPersistenceFailure_whenHandled_thenSameFailureIsReturned() = runTest {
        val expectedFailure = StorageFailure.DataNotFound
        val persistence = RecordingCellsConfigPersistence(Either.Left(expectedFailure))
        val handler = CellsConfigHandler(persistence)

        val result = handler.handle(CellsModel(Status.ENABLED))

        assertEquals(Either.Left(expectedFailure), result)
    }

    private class RecordingCellsConfigPersistence(
        private val result: Either<StorageFailure, Unit> = Either.Right(Unit),
    ) : CellsConfigPersistence {
        val enabledCalls = mutableListOf<Boolean>()
        val internalConfigCalls = mutableListOf<CellsInternalConfigModel?>()

        override suspend fun setCellsEnabled(enabled: Boolean): Either<StorageFailure, Unit> {
            enabledCalls += enabled
            return result
        }

        override suspend fun persistInternalCellsConfig(
            config: CellsInternalConfigModel?
        ): Either<StorageFailure, Unit> {
            internalConfigCalls += config
            return result
        }
    }
}
