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
package com.wire.kalium.cells.domain.usecase.versioning

import com.wire.kalium.cells.domain.FakeCellsRepository
import com.wire.kalium.cells.domain.model.NodeVersion
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetNodeVersionsUseCaseTest {

    @Test
    fun `GIVEN repository returns versions WHEN invoking use case THEN it returns expected list`() = runTest {
        val expectedVersions = listOf<NodeVersion>()
        fakeRepository.result = Either.Right(expectedVersions)
        val useCase = GetNodeVersionsUseCaseImpl(fakeRepository)

        val result = useCase(UUID)

        assertEquals(Either.Right(expectedVersions), result)
    }

    @Test
    fun `GIVEN repository returns failure WHEN invoking use case THEN it returns failure`() = runTest {
        val failure = Either.Left(NetworkFailure.ServerMiscommunication(IllegalStateException()))
        fakeRepository.result = failure
        val useCase = GetNodeVersionsUseCaseImpl(fakeRepository)

        val result = useCase(UUID)

        assertEquals(failure, result)
    }

    companion object {
        val fakeRepository = FakeCellsRepository()
        const val UUID = "123-456"
    }
}
