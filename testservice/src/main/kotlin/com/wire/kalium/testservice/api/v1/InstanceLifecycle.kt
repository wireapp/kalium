package com.wire.kalium.testservice.api.v1

import com.wire.kalium.testservice.models.Instance
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
class InstanceLifecycle {

    @GET
    @Path("/instances")
    fun getInstances(): List<String> {
        return listOf();
    }

    @PUT
    @Path("/instance")
    fun createInstance(): Instance {
        throw Exception("Not yet implemented")
    }

    @GET
    @Path("/instance/{id}")
    fun getInstance(@PathParam("id") id: String): Instance {
        throw Exception("Not yet implemented")
    }

    @DELETE
    @Path("/instance/{id}")
    fun deleteInstance(@PathParam("id") id: String): Instance {
        throw Exception("Not yet implemented")
    }

}
