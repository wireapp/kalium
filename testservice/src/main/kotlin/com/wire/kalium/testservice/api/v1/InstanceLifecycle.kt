package com.wire.kalium.testservice.api.v1

import com.wire.kalium.testservice.managed.InstanceService
import com.wire.kalium.testservice.models.Instance
import com.wire.kalium.testservice.models.InstanceRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.validation.Valid
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.WebApplicationException
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.ConnectionCallback
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.MediaType

@Api
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@ApiOperation("Instance life cycle")
class InstanceLifecycle(private val instanceService: InstanceService) {

    private val log = LoggerFactory.getLogger(InstanceLifecycle::class.java.name)

    @GET
    @Path("/instances")
    @ApiOperation(value = "Get all currently running instances")
    fun getInstances(): Collection<Instance> {
        return instanceService.getInstances()
    }

    @PUT
    @Path("/instance")
    @ApiOperation(value = "Create a new instance")
    fun createInstance(@Valid instanceRequest: InstanceRequest, @Suspended ar: AsyncResponse): Unit {
        val instanceId = UUID.randomUUID().toString()

        // handles unresponsive instances
        ar.setTimeout(170, TimeUnit.SECONDS) // TODO use configuration
        ar.setTimeoutHandler { asyncResponse: AsyncResponse ->
            log.error("Async create instance request timed out after 170 seconds")
            asyncResponse.cancel()
            instanceService.deleteInstance(instanceId)
        }
        // handles client disconnect
        ar.register(ConnectionCallback { disconnected: AsyncResponse? ->
            log.error("Client disconnected from async create instance request")
            instanceService.deleteInstance(instanceId)
        } as ConnectionCallback)

        val createdInstance = try {
            runBlocking {
                instanceService.createInstance(instanceId, instanceRequest)
            }
        } catch (e: Exception) {
            throw WebApplicationException("Could not create instance")
        }

        ar.resume(createdInstance)
    }

    @GET
    @Path("/instance/{id}")
    @ApiOperation(value = "Get information about an instance")
    fun getInstance(@PathParam("id") id: String): Instance {
        val instance = instanceService.getInstance(id)
        return instance ?: throw WebApplicationException("No instance found with id $id")
    }

    @DELETE
    @Path("/instance/{id}")
    @ApiOperation(value = "Delete an instance")
    fun deleteInstance(@PathParam("id") id: String) {
        val instance = instanceService.getInstance(id)
        if (instance == null) throw WebApplicationException("No instance found with id $id")
        instanceService.deleteInstance(id)
    }

}
