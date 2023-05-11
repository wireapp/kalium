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
