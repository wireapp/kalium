package com.wire.kalium.api.common

import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * this workaround solves kotlinx.coroutines.test.UncompletedCoroutinesError in tests
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runTestWithCancellation(body: suspend TestScope.() -> Unit): TestResult =
    try {
        runTest {
            body()
            cancel()
        }
    } catch (e: Exception) {
        if (e is CancellationException) {
            // ignore
        } else {
            throw e
        }
    }
