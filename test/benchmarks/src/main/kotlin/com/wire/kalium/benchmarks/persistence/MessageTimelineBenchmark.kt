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
package com.wire.kalium.benchmarks.persistence

import com.wire.kalium.benchmarks.timeline.MessageTimelineScenario
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Warm page loads, including database dispatch and mapping. Setup and cursor discovery are untimed. */
@State(Scope.Thread)
@Warmup(iterations = 3)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class MessageTimelineBenchmark {
    @Param("10000:0", "10000:1000", "10000:9000", "100000:0", "100000:1000", "100000:10000", "100000:90000")
    var position: String = "10000:0"

    @Param("TEXT", "MIXED", "MULTIPART", "REACTIONS", "MEMBERS", "SPARSE", "TIES")
    var profile: String = "TEXT"

    @Param("20", "60")
    var pageSize: Int = 20

    private lateinit var directory: File
    private lateinit var db: UserDatabaseBuilder
    private lateinit var scenario: MessageTimelineScenario

    @Setup
    fun setUp() {
        directory = Files.createTempDirectory("message-timeline-benchmark").toFile()
        val self = UserIDEntity("self", "benchmark.wire.com")
        db = database(directory, self)
        val (count, depth) = position.split(':').map(String::toInt)
        scenario = MessageTimelineScenario(db, self, count, depth, pageSize, profile)
        runBlocking { scenario.setUp() }
    }

    @Benchmark
    fun offsetFullEntities(blackhole: Blackhole) = runBlocking {
        blackhole.consume(scenario.offsetFullEntities())
    }

    @Benchmark
    fun keysetPageEntities(blackhole: Blackhole) = runBlocking {
        blackhole.consume(scenario.keysetPageEntities())
    }

    @Benchmark
    fun keysetListItems(blackhole: Blackhole) = runBlocking {
        blackhole.consume(scenario.keysetListItems())
    }

    @Benchmark
    fun newerListItems(blackhole: Blackhole) = runBlocking {
        blackhole.consume(scenario.newerListItems())
    }

    @Benchmark
    fun aroundMessage(blackhole: Blackhole) = runBlocking {
        blackhole.consume(scenario.aroundMessage())
    }

    @TearDown
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        if (::db.isInitialized) db.nuke()
        if (::directory.isInitialized) directory.deleteRecursively()
    }
}
