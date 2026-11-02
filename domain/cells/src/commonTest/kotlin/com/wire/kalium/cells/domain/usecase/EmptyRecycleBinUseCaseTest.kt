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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EmptyRecycleBinUseCaseTest {

    private val cellsRepository = mock<CellsRepository>(mode = MockMode.autoUnit)
    private val useCase = EmptyRecycleBinUseCaseImpl(cellsRepository)

    @Test
    fun givenAConversation_whenEmptyingItsRecycleBin_thenPermanentlyDeleteTheRecycleBinFolderAndReturnRight() = runTest {
        everySuspend { cellsRepository.deleteFiles(any(), any()) } returns Unit.right()

        val result = useCase("conversationId")

        assertEquals(Unit.right(), result)
        verifySuspend(VerifyMode.exactly(1)) {
            cellsRepository.deleteFiles(listOf("conversationId/recycle_bin"), permanentDelete = true)
        }
    }

    @Test
    fun givenTheRequestFails_whenEmptyingTheRecycleBin_thenReturnTheFailure() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        everySuspend { cellsRepository.deleteFiles(any(), any()) } returns failure.left()

        val result = useCase("conversationId")

        assertEquals(failure.left(), result)
    }
}
