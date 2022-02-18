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
    fun `given fold is called, when either is Right, applies fnR and returns its result`() {
        val either = Either.Right("Success")
        val result = either.fold({ fail("Shouldn't be executed") }) { 5 }

        assertSame(result, 5)
    }

    @Test
    fun `given fold is called, when either is Left, applies fnL and returns its result`() {
        val either = Either.Left(12)

        val foldResult = "Fold Result"
        val result = either.fold({ foldResult }) { fail("Shouldn't be executed") }

        assertSame(result, foldResult)
    }

    @Test
    fun `given flatMap is called, when either is Right, applies function and returns new Either`() {
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
    fun `given flatMap is called, when either is Left, doesn't invoke function and returns original Either`() {
        val either = Either.Left(12)

        val result: Either<Int, Int> = either.flatMap {
            fail("Shouldn't be executed")
        }

        assertTrue(result.isLeft())
        assertEquals<Either<Int, Int>>(result, either)
    }

    @Test
    fun `given onFailure is called, when either is Right, doesn't invoke function and returns original Either`() {
        val success = "Success"
        val either = Either.Right(success)

        val result = either.onFailure { fail("Shouldn't be executed") }

        assertSame(result, either)
        assertSame(either.getOrElse("Failure"), success)
    }

    @Test
    fun `given onFailure is called, when either is Left, invokes function with left value and returns original Either`() {
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
    fun `given onSuccess is called, when either is Right, invokes function with right value and returns original Either`() {
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
    fun `given onSuccess is called, when either is Left, doesn't invoke function and returns original Either`() {
        val either = Either.Left(12)

        val result = either.onSuccess {
            fail("Shouldn't be executed")
        }

        assertSame(result, either)
    }

    @Test
    fun `given map is called, when either is Right, invokes function with right value and returns a new Either`() {
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
    fun `given map is called, when either is Left, doesn't invoke function and returns original Either`() {
        val either: Either<Int, Int> = Either.Left(12)

        val result = either.map {
            fail("Shouldn't be executed")
        }

        assertTrue(result.isLeft())
        assertEquals(result, either)
    }

    @Test
    fun `given a list and a mapper to either that succeeds, when folding to either while right, then accumulate values until the end`() {
        val items = listOf(1, 1, 1)

        val result = items.foldToEitherWhileRight(0) { item, acc ->
            Either.Right(acc + item)
        }

        result shouldSucceed {
            assertEquals(it, 3)
        }
    }

    @Test
    fun `given a list and a mapper to either that fails, when folding to either while right, then return first Left`() {
        val items = listOf(1, 2, 3)

        val result = items.foldToEitherWhileRight(0) { _, _ ->
            Either.Left(-1)
        }

        result shouldFail {
            assertEquals(it, -1)
        }
    }

    @Test
    fun `given a list and a mapper to either that fails, when folding to either while right, mappers after left should not be called`() {
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
}
