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
package com.wire.kalium.logic.feature.legalhold

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkLegalHoldChangeAsNotifiedForSelfUseCaseTest {

    @Test
    fun givenASuccess_whenSettingLegalHoldChangeAsNotified_thenReturnSuccess() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withSetLegalHoldChangeNotifiedResult(Either.Right(Unit))
            .arrange()
        // when
        val result = useCase()
        // then
        assertEquals(MarkLegalHoldChangeAsNotifiedForSelfUseCase.Result.Success, result)
    }

    @Test
    fun givenAFailure_whenSettingLegalHoldChangeAsNotified_thenReturnSuccess() = runTest {
        // given
        val failure = StorageFailure.Generic(IOException())
        val (_, useCase) = Arrangement()
            .withSetLegalHoldChangeNotifiedResult(Either.Left(failure))
            .arrange()
        // when
        val result = useCase()
        // then
        assertEquals(MarkLegalHoldChangeAsNotifiedForSelfUseCase.Result.Failure(failure), result)
    }

    private class Arrangement {
                val userConfigRepository = mock(UserConfigRepository::class)
        val useCase: MarkLegalHoldChangeAsNotifiedForSelfUseCase = MarkLegalHoldChangeAsNotifiedForSelfUseCaseImpl(userConfigRepository)

        fun arrange() = this to useCase
        suspend fun withSetLegalHoldChangeNotifiedResult(result: Either<StorageFailure, Unit>) = apply {
            coEvery {
                userConfigRepository.setLegalHoldChangeNotified(any())
            }.returns(result)
        }
    }
}
