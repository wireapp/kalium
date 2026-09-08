/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.benchmarks.android

import android.content.Context
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.wire.kalium.benchmarks.timeline.MessageTimelineScenario
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Opt-in SQLCipher measurements using the same fixtures and operations as the JVM suite. */
@RunWith(Parameterized::class)
class MessageTimelineBenchmark(private val position: String, private val profile: String, private val pageSize: Int) {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: UserDatabaseBuilder
    private lateinit var scenario: MessageTimelineScenario

    @Volatile
    private var lastResult: Any? = null

    @Before
    fun setUp() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runMessageTimelineBenchmarks") == "true")
        BenchmarkDb.delete(context)
        db = BenchmarkDb.build(context)
        val (count, depth) = position.split(':').map(String::toInt)
        scenario = MessageTimelineScenario(db, BenchmarkDb.selfUserId, count, depth, pageSize, profile)
        runBlocking { scenario.setUp() }
    }

    @Test
    fun offsetFullEntities() = benchmarkRule.measureRepeated {
        lastResult = runBlocking { scenario.offsetFullEntities() }
    }

    @Test
    fun keysetPageEntities() = benchmarkRule.measureRepeated {
        lastResult = runBlocking { scenario.keysetPageEntities() }
    }

    @Test
    fun keysetListItems() = benchmarkRule.measureRepeated {
        lastResult = runBlocking { scenario.keysetListItems() }
    }

    @Test
    fun newerListItems() = benchmarkRule.measureRepeated {
        lastResult = runBlocking { scenario.newerListItems() }
    }

    @Test
    fun aroundMessage() = benchmarkRule.measureRepeated {
        lastResult = runBlocking { scenario.aroundMessage() }
    }

    @After
    fun tearDown() {
        lastResult = null
        if (::scenario.isInitialized) scenario.close()
        if (::db.isInitialized) db.nuke()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}-page{2}")
        fun parameters(): List<Array<Any>> {
            val args = InstrumentationRegistry.getArguments()
            val positions = args.getString("timelinePosition")?.let(::listOf) ?: MessageTimelineScenario.POSITIONS
            val profiles = args.getString("timelineProfile")?.let(::listOf) ?: MessageTimelineScenario.PROFILES
            val pageSizes = args.getString("timelinePageSize")?.toInt()?.let(::listOf) ?: listOf(20, 60)
            return positions.flatMap { position ->
                profiles.flatMap { profile -> pageSizes.map { arrayOf(position, profile, it) } }
            }
        }
    }
}
