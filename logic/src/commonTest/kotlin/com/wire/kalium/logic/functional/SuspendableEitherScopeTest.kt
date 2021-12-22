package com.wire.kalium.logic.functional

import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.exceptions.KaliumException
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class SuspendableEitherScopeTest {

    private lateinit var suspendableEitherScope: SuspendableEitherScope

    @BeforeTest
    fun setUp() {
        suspendableEitherScope = SuspendableEitherScope()
    }

    @Test
    fun `given either is right, when folding, applies fnR and returns its result`() = runTest {
        val either = Either.Right("Success")

        val result = with(suspendableEitherScope) {
            either.fold({ fail("Shouldn't be executed") }) { 5 }
        }

        assertEquals(result, 5)
    }

    @Test
    fun `given either is left, when folding, applies fnL and returns its result`() = runTest {
        val either = Either.Left(12)
        val foldResult = "Fold Result"

        val result = with(suspendableEitherScope) {
            either.fold({ foldResult }) { fail("Shouldn't be executed") }
        }

        assertSame(result, foldResult)
    }

    @Test
    fun `given either is Right, when flatMapping, applies function and returns new Either`() = runTest {
        val either = Either.Right("Success")

        val mapped = Either.Left(KaliumException.GenericError(null, null))
        val result = with(suspendableEitherScope) {
            either.flatMap {
                assertSame(it, "Success")
                mapped
            }
        }

        assertEquals(result, mapped)
        assertTrue(result.isLeft())
    }

    @Test
    fun `given either is Left, when flatMapping, doesn't invoke function and returns original Either`() = runTest {
        val either = Either.Left(12)

        val result: Either<Int, Int> = with(suspendableEitherScope) {
            either.flatMap { fail("Shouldn't be executed") }
        }

        assertTrue(result.isLeft())
        assertEquals<Either<Int, Int>>(result, either)
    }

    @Test
    fun `given either is right, when onFailure is called, doesn't invoke function and returns original Either`() = runTest {
        val success = "Success"
        val either = Either.Right(success)

        val result = with(suspendableEitherScope) {
            either.onFailure { fail("Shouldn't be executed") }
        }

        assertSame(result, either)
        assertSame(either.getOrElse("Failure"), success)
    }

    @Test
    fun `given either is left, when onFailure is called, invokes function with left value and returns original Either`() =
        runTest {
            val either = Either.Left(12)
            var methodCalled = false

            val result = with(suspendableEitherScope) {

                either.onFailure {
                    assertEquals(it, 12)
                    methodCalled = true
                }
            }

            assertSame(result, either)
            assertSame(methodCalled, true)
        }

    @Test
    fun `given either is right, when onSuccess is called, invokes function with right value and returns original Either`() =
        runTest {
            val success = "Success"
            val either = Either.Right(success)
            var methodCalled = false

            val result = with(suspendableEitherScope) {
                either.onSuccess {
                    assertEquals(it, success)
                    methodCalled = true
                }
            }

            assertSame(result, either)
            assertSame(methodCalled, true)
        }

    @Test
    fun `given either is left, when onSuccess is called, doesn't invoke function and returns original Either`() = runTest {
        val either = Either.Left(12)

        val result = with(suspendableEitherScope) {
            either.onSuccess { fail("Shouldn't be executed") }
        }

        assertSame(result, either)
    }

    @Test
    fun `given either is right, when mapping, invokes function with right value and returns a new Either`() = runTest {
        val success = "Success"
        val resultValue = "Result"
        val either = Either.Right(success)

        val result = with(suspendableEitherScope) {

            either.map {
                assertSame(it, success)
                resultValue
            }
        }

        assertEquals(result, Either.Right(resultValue))
    }

    @Test
    fun `given either is left, when mapping, doesn't invoke function and returns original Either`() = runTest {
        val either = Either.Left(12)

        val result = with(suspendableEitherScope) {
            either.map { fail("Shouldn't be executed") }
        }

        assertTrue(result.isLeft())
        assertEquals<Either<Int, Int>>(result, either)
    }

    @Test
    fun `given a block, when suspending is called, then creates a new SuspendableEitherScope and executes block on it`() {
        var callCount = 0
        val block: SuspendableEitherScope.() -> Unit = { callCount++ }

        runTest {
            suspending {
                assertIs<SuspendableEitherScope>(this)
                block()
            }
        }

        assertEquals(callCount, 1)
    }

    @Test
    fun `given a list and a mapper to either that succeeds, when folding to either while right, then accumulate values until the end`() =
        runTest {
            val items = listOf(1, 1, 1)

            val result =
                suspending {
                    items.foldToEitherWhileRight(0) { item, acc ->
                        Either.Right(acc + item)
                    }
                }

            result shouldSucceed {
                assertEquals(it, 3)
            }
        }

    @Test
    fun `given a list and a mapper to either that fails, when folding to either while right, then return first Left`() = runTest {
        val items = listOf(1, 2, 3)

        val result = suspending {
            items.foldToEitherWhileRight(0) { _, _ ->
                Either.Left(-1)
            }
        }

        result shouldFail {
            assertEquals(it, -1)
        }
    }

    @Test
    fun `given a list and a mapper to either that fails, when folding to either while right, mappers after left should not be called`() =
        runTest {
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
