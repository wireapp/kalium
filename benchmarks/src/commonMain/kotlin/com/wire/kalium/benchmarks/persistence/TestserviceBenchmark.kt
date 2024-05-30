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
package com.wire.kalium.benchmarks.persistence

import com.codahale.metrics.MetricRegistry
import com.wire.kalium.testservice.TestserviceConfiguration
import com.wire.kalium.testservice.managed.InstanceService
import com.wire.kalium.testservice.models.InstanceRequest
import io.dropwizard.lifecycle.setup.LifecycleEnvironment
import io.dropwizard.lifecycle.setup.ScheduledExecutorServiceBuilder
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Level
import java.util.UUID
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Warmup(iterations = 3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 10)
class TestserviceBenchmark {

    @Benchmark
    fun instanceCreation(testData: TestData) = runBlocking {
        val instanceRequest = InstanceRequest(email = testData.email, password = testData.password)
        runBlocking {
            testData.instanceService.createInstance(testData.instanceId, instanceRequest)
        }
    }

    @State(value = Scope.Benchmark)
    class TestData {
        lateinit var email: String
        lateinit var password: String
        lateinit var instanceId: String
        lateinit var instanceService: InstanceService

        @Setup(Level.Trial)
        fun setUp() {
            // Create user on staging backend
            val user = BackendSetup("https://staging-nginz-https.zinfra.io/",
                BasicAuth(System.getenv("BASIC_AUTH_ENCODED"))
            ).createUser()

            // Create mock instanceService
            val metricRegistry = MetricRegistry()
            val environment = LifecycleEnvironment(metricRegistry)
            val cleanupPool = ScheduledExecutorServiceBuilder(environment, "cleanupPool", true)
                .threads(2)
                .removeOnCancelPolicy(true)
                .build()
            val configuration = TestserviceConfiguration()
            instanceService = InstanceService(metricRegistry, cleanupPool, configuration)
            instanceId = UUID.randomUUID().toString()
            email = user.email
            password = user.password
        }

        @TearDown(Level.Trial)
        fun tearDown() {
            // Delete user from staging backend
        }
    }

}
