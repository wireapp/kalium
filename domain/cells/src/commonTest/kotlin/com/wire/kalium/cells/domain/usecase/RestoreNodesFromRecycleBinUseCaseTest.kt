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

import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
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

class RestoreNodesFromRecycleBinUseCaseTest {

    @Test
    fun givenNodes_whenRestoreSucceeds_thenRestoreAllInOneRequestAndReturnRight() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRestoreReturning(Unit.right())
            .arrange()

        val result = useCase(UUIDS)

        assertEquals(Unit.right(), result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.cellsRepository.restoreNodes(UUIDS) }
    }

    @Test
    fun givenNodes_whenRestoreSucceeds_thenRefreshTheAttachmentStatusOfAllOfThemAtOnce() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRestoreReturning(Unit.right())
            .arrange()

        useCase(UUIDS)

        verifySuspend(VerifyMode.exactly(1)) { arrangement.attachmentsRepository.refreshRestoredAttachmentStatus(UUIDS) }
    }

    @Test
    fun givenNodes_whenRestoreFails_thenReturnFailureAndKeepAttachmentStatus() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, useCase) = Arrangement()
            .withRestoreReturning(failure.left())
            .arrange()

        val result = useCase(UUIDS)

        assertEquals(failure.left(), result)
        verifySuspend(VerifyMode.not) { arrangement.attachmentsRepository.refreshRestoredAttachmentStatus(any()) }
    }

    @Test
    fun givenNoNodes_whenInvoked_thenReturnRightWithoutRequest() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRestoreReturning(Unit.right())
            .arrange()

        val result = useCase(emptyList())

        assertEquals(Unit.right(), result)
        verifySuspend(VerifyMode.not) { arrangement.cellsRepository.restoreNodes(any()) }
    }

    private class Arrangement {

        val cellsRepository = mock<CellsRepository>(mode = MockMode.autoUnit)
        val attachmentsRepository = mock<CellAttachmentsRepository>(mode = MockMode.autoUnit)

        suspend fun withRestoreReturning(result: Either<NetworkFailure, Unit>) = apply {
            everySuspend { cellsRepository.restoreNodes(any()) } returns result
        }

        suspend fun arrange(): Pair<Arrangement, RestoreNodesFromRecycleBinUseCase> {
            everySuspend { attachmentsRepository.refreshRestoredAttachmentStatus(any()) } returns Unit.right()

            return this to RestoreNodesFromRecycleBinUseCaseImpl(
                cellsRepository = cellsRepository,
                attachmentsRepository = attachmentsRepository,
            )
        }
    }

    private companion object {
        val UUIDS = listOf("uuid-1", "uuid-2")
    }
}
