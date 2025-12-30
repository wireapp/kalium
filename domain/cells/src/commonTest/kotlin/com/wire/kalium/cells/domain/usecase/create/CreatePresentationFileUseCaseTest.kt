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

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CreatePresentationFileUseCaseTest {

    @Test
    fun `given path, when invoke is called, then it calls repository with correct parameters`() = runTest {
        // Given
        val path = "some/path"
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturning(Either.Right(listOf()))
            .arrange()

        // When
        useCase(path)

        // Then
        coVerify {
            arrangement.cellsRepository.createFile(
                folderName = path + CreatePresentationFileUseCaseImpl.EXTENSION,
                contentType = CreatePresentationFileUseCaseImpl.CONTENT_TYPE,
                templateUuid = CreatePresentationFileUseCaseImpl.PRESENTATION_TEMPLATE_UUID
            )
        }.wasInvoked()
    }

    @Test
    fun `given failure from repository, when invoke is called, then it returns failure`() = runTest {
        // Given
        val path = "some/path"
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

        val cellsRepository = mock(CellsRepository::class)

        suspend fun withRepositoryReturning(result: Either<NetworkFailure, List<CellNode>>) = apply {
            coEvery {
                cellsRepository.createFile(
                    folderName = any(),
                    contentType = any(),
                    templateUuid = any()
                )
            }.returns(result)
        }

        fun arrange() = this to CreatePresentationFileUseCaseImpl(
            cellsRepository = cellsRepository
        )
    }
}
