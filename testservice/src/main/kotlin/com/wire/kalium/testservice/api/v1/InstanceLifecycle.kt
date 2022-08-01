package com.wire.kalium.testservice.api.v1

import com.wire.kalium.testservice.managed.InstanceService
import com.wire.kalium.testservice.models.Instance
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.MediaType

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
class InstanceLifecycle(private val instanceService: InstanceService) {

    @GET
    @Path("/instances")
    fun getInstances(): List<Instance> {
        return instanceService.getInstances()
    }

    @PUT
    @Path("/instance")
    fun createInstance(): Instance {
        throw WebApplicationException("Not yet implemented")
    }

    @GET
    @Path("/instance/{id}")
    fun getInstance(@PathParam("id") id: String): Instance {
        val instance = instanceService.getInstance(id)
        return if (instance != null) {
            instance
        } else throw WebApplicationException("No instance found with id $id")
    }

    @DELETE
    @Path("/instance/{id}")
    fun deleteInstance(@PathParam("id") id: String): Instance {
        throw WebApplicationException("Not yet implemented")
    }

}
