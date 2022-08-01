package com.wire.kalium.testservice.managed

import com.wire.kalium.testservice.models.InstanceRequest
import com.wire.kalium.testservice.models.Instance
import io.dropwizard.lifecycle.Managed
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/*
This service makes sure that instances are destroyed (used files deleted) on
shutdown of the service and also periodically checks for leftover instances
which are not needed anymore.
 */
class InstanceService : Managed {

    private val log = LoggerFactory.getLogger(InstanceService::class.java.name)
    private val instances: MutableMap<String, Instance> = ConcurrentHashMap<String, Instance>()

    override fun start() {
        log.info("Instance service started.")
    }

    override fun stop() {
        log.info("Instance service stopping...")
        log.info("Instance service stopped.")
    }

    fun getInstances(): Collection<Instance> {
        return instances.values
    }

    fun getInstance(id: String): Instance? {
        return instances.get(id)
    }

    fun createInstance(instanceRequest: InstanceRequest): Instance? {
        val uuid = UUID.randomUUID()
        val instance = Instance()
        instances.put(uuid.toString(), instance)
        return instance
    }

    fun deleteInstance(id: String): Unit {
        val instance = instances.get(id)
        // TODO: shutdown instance
        instances.remove(id)
    }

}
