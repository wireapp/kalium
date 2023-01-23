package com.wire.kalium.util

import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@Ignore
class DateTimeUtilBenchmark {

    @OptIn(ExperimentalTime::class)
    @Test
    fun instantToIso() = runTest {
        val numberOfInstants = 1_000_000

        val instants = Array(numberOfInstants) {
            Instant.fromEpochMilliseconds(Random.nextLong())
        }

        measureTime {
            instants.forEach { it.toIsoDateTimeString() }
        }.also {
            println("Took $it to convert $numberOfInstants into ISO date time")
        }
    }
}
