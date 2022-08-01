package com.wire.kalium.testservice.api.v1

import com.wire.kalium.testservice.models.Instance
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
class ClientResources {

    // Set a user's availability.
    @POST
    @Path("/instance/{id}/availability")
    fun availability(@PathParam("id") id: String): Instance {
        throw Exception("Not yet implemented")
    }

    // GET /api/v1/instance/{instanceId}/clients
    // Get all clients of an instance.

    // GET /api/v1/instance/{instanceId}/fingerprint
    // Get the fingerprint from the instance's client.

    // POST /api/v1/instance/{instanceId}/breakSession
    // Break a session to a specific device of a remote user (on purpose).

    // POST /api/v1/instance/{instanceId}/sendSessionReset
    // Reset session of a specific device
}
