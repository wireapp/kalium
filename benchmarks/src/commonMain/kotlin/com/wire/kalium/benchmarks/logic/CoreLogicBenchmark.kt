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
package com.wire.kalium.benchmarks.logic

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.concurrent.TimeUnit


@State(Scope.Benchmark)
@Warmup(iterations = 3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 10)
class CoreLogicBenchmark {

    private var tempPath = ""
    private val userAgent = "KaliumTestService/${System.getProperty("http.agent")}"

    @Setup
    fun prepare() {
        val tempDirectory = Files.createTempDirectory("coreLogicBenchmark").toFile()
        tempDirectory.deleteOnExit()
        tempPath = tempDirectory.absolutePath
    }

    @Benchmark
    fun createObjectInFiles(blackHole: Blackhole) = runBlocking {
        val kaliumConfigs = KaliumConfigs(
            developmentApiEnabled = false
        )
        val coreLogic = CoreLogic(tempPath, kaliumConfigs, userAgent, useInMemoryStorage = false)
        blackHole.consume(coreLogic)
        val result = coreLogic.versionedAuthenticationScope(ServerConfig.STAGING).invoke(null)
        blackHole.consume(result)
    }

    @Benchmark
    fun createObjectInMemory(blackHole: Blackhole) = runBlocking {
        val kaliumConfigs = KaliumConfigs(
            developmentApiEnabled = false
        )
        val coreLogic = CoreLogic(tempPath, kaliumConfigs, userAgent, useInMemoryStorage = true)
        blackHole.consume(coreLogic)
        val result = coreLogic.versionedAuthenticationScope(ServerConfig.STAGING).invoke(null)
        blackHole.consume(result)
    }

}
