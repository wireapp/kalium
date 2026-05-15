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
package com.wire.kalium.testservice

import com.codahale.metrics.MetricRegistry
import com.wire.kalium.testservice.managed.InstanceService
import com.wire.kalium.testservice.models.InstanceRequest
import io.dropwizard.lifecycle.setup.LifecycleEnvironment
import io.dropwizard.lifecycle.setup.ScheduledExecutorServiceBuilder
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File
import java.util.UUID
import javax.ws.rs.WebApplicationException

class InstanceServiceTest {

    @Test
    fun givenWorkingCredentials_whenInstanceCreated_thenAllDataIsStoredCorrectly() {
        val metricRegistry = MetricRegistry()
        val environment = LifecycleEnvironment(metricRegistry)
        val cleanupPool = ScheduledExecutorServiceBuilder(environment, "cleanupPool", true)
            .threads(2)
            .removeOnCancelPolicy(true)
            .build()
        val configuration = TestserviceConfiguration()
        val instanceService = InstanceService(metricRegistry, cleanupPool, configuration)
        val instanceId = UUID.randomUUID().toString()
        val instanceRequest = InstanceRequest()
        val instancePath = System.getProperty("user.home") +
                File.separator + ".testservice" + File.separator + instanceId
        runBlocking {
            try {
                instanceService.createInstance(instanceId, instanceRequest)
            } catch (exception: WebApplicationException) {
                println("Expected exception $exception thrown")
            }
        }
        assertThat("No directory for coreLogic was created",
            File(instancePath).exists())
        assertThat("No global-storage/global-db file found in $instancePath",
            File("$instancePath/global-storage/global-db").exists())
    }
}
