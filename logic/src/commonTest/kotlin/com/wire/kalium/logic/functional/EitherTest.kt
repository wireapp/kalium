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

package com.wire.kalium.logic.functional

import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.exceptions.KaliumException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class EitherTest {

    @Test
    fun givenFoldIsCalled_whenEitherIsRight_appliesFnRAndReturnsItsResult() {
        val either = Either.Right("Success")
        val result = either.fold({ fail("Shouldn't be executed") }) { 5 }

        assertSame(result, 5)
    }

    @Test
    fun givenFoldIsCalled_whenEitherIsLeft_appliesFnLAndReturnsItsResult() {
        val either = Either.Left(12)

        val foldResult = "Fold Result"
        val result = either.fold({ foldResult }) { fail("Shouldn't be executed") }

        assertSame(result, foldResult)
    }

    @Test
    fun givenFlatMapIsCalled_whenEitherIsRight_appliesFunctionAndReturnsNewEither() {
        val either = Either.Right("Success")

        val mapped = Either.Left(KaliumException.GenericError(KaliumException.GenericError(IllegalStateException())))
        val result = either.flatMap {
            assertSame(it, "Success")
            mapped
        }

        assertTrue(result.isLeft())
        assertEquals<Either<KaliumException.GenericError, Int>>(result, mapped)
    }

    @Test
    fun givenFlatMapIsCalled_whenEitherIsLeft_doesNotInvokeFunctionAndReturnsOriginalEither() {
        val either = Either.Left(12)

        val result: Either<Int, Int> = either.flatMap {
            fail("Shouldn't be executed")
        }

        assertTrue(result.isLeft())
        assertEquals<Either<Int, Int>>(result, either)
    }

    @Test
    fun givenOnFailureIsCalled_whenEitherIsRight_doesNotInvokeFunctionAndReturnsOriginalEither() {
        val success = "Success"
        val either = Either.Right(success)

        val result = either.onFailure { fail("Shouldn't be executed") }

        assertSame(result, either)
        assertSame(either.getOrElse("Failure"), success)
    }

    @Test
    fun givenOnFailureIsCalled_whenEitherIsLeft_invokesFunctionWithLeftValueAndReturnsOriginalEither() {
        val either = Either.Left(12)

        var methodCalled = false
        val result = either.onFailure {
            assertEquals(it, 12)
            methodCalled = true
        }

        assertSame(result, either)
        assertTrue(methodCalled)
    }

    @Test
    fun givenOnSuccessIsCalled_whenEitherIsRight_invokesFunctionWithRightValueAndReturnsOriginalEither() {
        val success = "Success"
        val either = Either.Right(success)

        var methodCalled = false
        val result = either.onSuccess {
            assertEquals(it, success)
            methodCalled = true
        }

        assertSame(result, either)
        assertSame(methodCalled, true)
    }

    @Test
    fun givenOnSuccessIsCalled_whenEitherIsLeft_doesNotInvokeFunctionAndReturnsOriginalEither() {
        val either = Either.Left(12)

        val result = either.onSuccess {
            fail("Shouldn't be executed")
        }

        assertSame(result, either)
    }

    @Test
    fun givenMapIsCalled_whenEitherIsRight_invokesFunctionWithRightValueAndReturnsANewEither() {
        val success = "Success"
        val resultValue = "Result"
        val either = Either.Right(success)

        val result = either.map {
            assertSame(it, success)
            resultValue
        }

        assertEquals(result, Either.Right(resultValue))
    }

    @Test
    fun givenMapIsCalled_whenEitherIsLeft_doesNotInvokeFunctionAndReturnsOriginalEither() {
        val either: Either<Int, Int> = Either.Left(12)

        val result = either.map {
            fail("Shouldn't be executed")
        }

        assertTrue(result.isLeft())
        assertEquals(result, either)
    }

    @Test
    fun givenAListAndAMapperToEitherThatSucceeds_whenFoldingToEitherWhileRight_thenAccumulateValuesUntilTheEnd() {
        val items = listOf(1, 1, 1)

        val result = items.foldToEitherWhileRight(0) { item, acc ->
            Either.Right(acc + item)
        }

        result shouldSucceed {
            assertEquals(it, 3)
        }
    }

    @Test
    fun givenAListAndAMapperToEitherThatFails_whenFoldingToEitherWhileRight_thenReturnFirstLeft() {
        val items = listOf(1, 2, 3)

        val result = items.foldToEitherWhileRight(0) { _, _ ->
            Either.Left(-1)
        }

        result shouldFail {
            assertEquals(it, -1)
        }
    }

    @Test
    fun givenAListAndAMapperToEitherThatFails_whenFoldingToEitherWhileRight_mappersAfterLeftShouldNotBeCalled() {
        var callCount = 0
        val mapFunction: () -> Either<Int, Int> = {
            callCount++
            fail("Shouldn't be executed")
        }
        val expectedFailure = -1
        val items = listOf(1 to { Either.Left(expectedFailure) }, 2 to mapFunction, 3 to mapFunction)

        items.foldToEitherWhileRight(0) { item, _ ->
            item.second()
        }

        assertEquals(callCount, 0)
    }

    @Test
    fun givenGetOrFailIsCalled_whenEitherIsRight_returnsRValue() {
        val expected = "Expected"
        val either = Either.Right(expected)
        val result = either.getOrFail {
            fail("Shouldn't be executed")
        }
        assertSame(result, expected)
    }

    @Test
    fun givenGetOrFailIsCalled_whenEitherIsLeft_returnsUniValueAndRunFL() {
        val expected = "Expected"
        val either = Either.Left(expected)
        val result = either.getOrFail {
            assertSame(it, expected)
        }
        assertSame(result, Unit)
    }

    @Test
    fun givenGetOrFailIsCalledInBlock_whenEitherIsLeft_innerReturnCalled() {
        val expected = "Expected"
        val either = Either.Left(expected)
        val failReturn: String = run {
            either.getOrFail {
                assertSame(it, expected)
                return@run it
            }
            fail("Shouldn't be executed")
        }
        assertSame(failReturn, expected)
    }

    @Test
    fun givenGetOrFailIsCalledInBlock_whenEitherIsRight_innerReturnNotCalled() {
        val expected = "Expected"
        val either = Either.Right(expected)
        val result: String = run {
            val value = either.getOrFail {
                fail("Shouldn't be executed")
            }
            return@run value
        }
        assertSame(result, expected)
    }
}
