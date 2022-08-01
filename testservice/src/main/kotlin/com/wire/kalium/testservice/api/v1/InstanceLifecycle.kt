package com.wire.kalium.testservice.api.v1

import com.wire.kalium.testservice.managed.InstanceService
import com.wire.kalium.testservice.models.InstanceRequest
import com.wire.kalium.testservice.models.Instance
import io.swagger.annotations.Api
import javax.validation.Valid
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.WebApplicationException
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.MediaType

@Api
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
class InstanceLifecycle(private val instanceService: InstanceService) {

    @GET
    @Path("/instances")
    fun getInstances(): Collection<Instance> {
        return instanceService.getInstances()
    }

    @PUT
    @Path("/instance")
    fun createInstance(@Valid instanceRequest: InstanceRequest, @Suspended ar: AsyncResponse): Instance {
        val createdInstance = instanceService.createInstance(instanceRequest);
        return createdInstance ?: throw WebApplicationException("Could not create instance")
    }

    @GET
    @Path("/instance/{id}")
    fun getInstance(@PathParam("id") id: String): Instance {
        val instance = instanceService.getInstance(id)
        return instance ?: throw WebApplicationException("No instance found with id $id")
    }

    @DELETE
    @Path("/instance/{id}")
    fun deleteInstance(@PathParam("id") id: String) {
        val instance = instanceService.getInstance(id)
        if (instance == null) throw WebApplicationException("No instance found with id $id")
        instanceService.deleteInstance(id)
    }

}
