package com.wire.kalium.testservice.managed

import com.wire.kalium.testservice.models.Instance
import io.dropwizard.lifecycle.Managed
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/*
This service makes sure that instances are destroyed (used files deleted) on
shutdown of the service and also periodically checks for leftover instances
which are not needed anymore.
 */
class InstanceService: Managed {

    private val log = LoggerFactory.getLogger(InstanceService::class.java.name)
    private val instances: Map<String, Instance> = ConcurrentHashMap<String, Instance>()

    override fun start() {
        log.info("Instance service started.")
    }

    override fun stop() {
        log.info("Instance service stopping...")
        log.info("Instance service stopped.")
    }

    fun getInstances(): List<Instance> {
        return listOf()
    }

    fun getInstance(id: String): Instance? {
        return instances.get(id)
    }

}
