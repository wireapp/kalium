package com.wire.kalium.testservice.api.v1

import com.wire.kalium.testservice.managed.InstanceService
import com.wire.kalium.testservice.models.Instance
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.slf4j.LoggerFactory
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
class ClientResources(private val instanceService: InstanceService) {

    private val log = LoggerFactory.getLogger(ClientResources::class.java.name)

    @POST
    @Path("/instance/{id}/availability")
    @Operation(summary = "Set a user's availability")
    fun availability(@PathParam("id") id: String): Instance {
        throw WebApplicationException("Instance $id: Not yet implemented")
    }

    // GET /api/v1/instance/{instanceId}/clients
    // Get all clients of an instance.

    @GET
    @Path("/instance/{id}/fingerprint")
    @Operation(summary = "Get the fingerprint from the instance's client")
    @ApiResponse(
        responseCode = "200",
        content = [
            Content(
                mediaType = "application/json",
                examples = [
                    ExampleObject(
                        value = "{ \"fingerprint\": "
                                +"\"5badc6060241ce1000eba81f09cd42cfc3b5d6a951870f22bc5b9a5acc5b14af\","
                                + "\"id\": \"1a3d8b57-ef71-4d0e-b046-ce6f4cb825c2\" }"
                    )
                ]
            )
        ]
    )
    fun fingerprint(@PathParam("id") id: String): Response {
        instanceService.getInstance(id) ?: throw WebApplicationException("No instance found with id $id")
        return instanceService.getFingerprint(id)
    }

    // POST /api/v1/instance/{instanceId}/breakSession
    // Break a session to a specific device of a remote user (on purpose).

    // POST /api/v1/instance/{instanceId}/sendSessionReset
    // Reset session of a specific device
}
