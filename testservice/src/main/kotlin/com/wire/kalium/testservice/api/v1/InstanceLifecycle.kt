package com.wire.kalium.testservice.api.v1

import com.wire.kalium.testservice.TestserviceConfiguration
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
class InstanceLifecycle(private val instanceService: InstanceService,
                        private val configuration: TestserviceConfiguration) {

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
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun createInstance(@Valid instanceRequest: InstanceRequest, @Suspended ar: AsyncResponse) {
        val instanceId = UUID.randomUUID().toString()
        val timeout = configuration.getInstanceCreationTimeoutInSeconds()

        // handles unresponsive instances
        ar.setTimeout(timeout, TimeUnit.SECONDS)
        ar.setTimeoutHandler { asyncResponse: AsyncResponse ->
            log.error("Async create instance request timed out after $timeout seconds")
            asyncResponse.cancel()
            instanceService.deleteInstance(instanceId)
        }
        // handles client disconnect
        ar.register(ConnectionCallback { disconnected: AsyncResponse? ->
            log.error("Client disconnected from async create instance request")
            instanceService.deleteInstance(instanceId)
        })

        val createdInstance = try {
            runBlocking {
                instanceService.createInstance(instanceId, instanceRequest)
            }
        } catch (we: WebApplicationException) {
            throw we
        } catch (e: Exception) {
            log.error("Could not create instance: " + e.message, e)
            throw WebApplicationException("Could not create instance: " + e.message)
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
        instanceService.getInstance(id) ?: throw WebApplicationException("No instance found with id $id")
        instanceService.deleteInstance(id)
    }

}
