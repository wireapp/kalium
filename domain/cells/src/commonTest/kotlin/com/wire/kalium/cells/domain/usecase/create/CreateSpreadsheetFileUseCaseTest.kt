/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.cells.domain.usecase.create

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CreateSpreadsheetFileUseCaseTest {

    @Test
    fun given_pathWhen_invoke_is_calledThen_it_calls_repository_with_correct_parameters() = runTest {
        // Given
        val path = "some/spreadsheet/path"
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturning(Either.Right(listOf()))
            .arrange()

        // When
        useCase(path)

        // Then
        verifySuspend {
            arrangement.cellsRepository.createFile(
                folderName = path + CreateSpreadsheetFileUseCaseImpl.EXTENSION,
                contentType = CreateSpreadsheetFileUseCaseImpl.CONTENT_TYPE,
                templateUuid = CreateSpreadsheetFileUseCaseImpl.SPREADSHEET_TEMPLATE_UUID
            )
        }
    }

    @Test
    fun given_failure_from_repositoryWhen_invoke_is_calledThen_it_returns_failure() = runTest {
        // Given
        val path = "some/spreadsheet/path"
        val expectedFailure = NetworkFailure.FeatureNotSupported
        val (_, useCase) = Arrangement()
            .withRepositoryReturning(Either.Left(expectedFailure))
            .arrange()

        // When
        val result = useCase(path)

        // Then
        assertEquals(Either.Left(expectedFailure), result)
    }

    private class Arrangement {

        val cellsRepository = mock<CellsRepository>(mode = MockMode.autoUnit)

        suspend fun withRepositoryReturning(result: Either<NetworkFailure, List<CellNode>>) = apply {
            everySuspend {
                cellsRepository.createFile(
                    folderName = any(),
                    contentType = any(),
                    templateUuid = any()
                )
            }.returns(result)
        }

        fun arrange() = this to CreateSpreadsheetFileUseCaseImpl(
            cellsRepository = cellsRepository
        )
    }
}
