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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateNodeTagsUseCaseTest {

    @Test
    fun given_repository_updates_tags_successfullyWhen_invoke_is_calledThen_return_Right_Unit() = runTest {
        val uuid = "node-123"
        val tags = listOf("tag1", "tag2")
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturning(Unit.right())
            .arrange()

        val result = useCase.invoke(uuid, tags)

        assertEquals(Unit.right(), result)
        coVerify {
            arrangement.cellsRepository.updateNodeTags(uuid, tags)
        }.wasInvoked(once)
    }

    @Test
    fun given_repository_fails_to_update_tagsWhen_invoke_is_calledThen_return_Left_failure() = runTest {
        val uuid = "node-456"
        val tags = listOf("tagA", "tagB")
        val failure = NetworkFailure.FeatureNotSupported

        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturning(failure.left())
            .arrange()

        val result = useCase.invoke(uuid, tags)

        assertEquals(failure.left(), result)
        coVerify {
            arrangement.cellsRepository.updateNodeTags(uuid, tags)
        }.wasInvoked(once)
    }

    private class Arrangement {

        val cellsRepository = mock(CellsRepository::class)

        suspend fun withRepositoryReturning(result: Either<NetworkFailure, Unit>) = apply {
            coEvery { cellsRepository.updateNodeTags(any(), any()) }.returns(result)
        }

        fun arrange() = this to UpdateNodeTagsUseCaseImpl(
            cellsRepository = cellsRepository
        )
    }
}
