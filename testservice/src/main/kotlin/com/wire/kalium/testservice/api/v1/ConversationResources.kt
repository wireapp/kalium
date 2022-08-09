package com.wire.kalium.testservice.api.v1

import com.wire.kalium.testservice.managed.InstanceService
import com.wire.kalium.testservice.models.Instance
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.MediaType

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
class ConversationResources(private val instanceService: InstanceService) {

    // archive a conversation
    @POST
    @Path("/instance/{id}/archive")
    fun archive(@PathParam("id") id: String): Instance {
        throw WebApplicationException("Not yet implemented")
    }

    // clear a conversation
    @POST
    @Path("/instance/{id}/clear")
    fun clear(@PathParam("id") id: String): Instance {
        throw WebApplicationException("Not yet implemented")
    }

    // POST /api/v1/instance/{instanceId}/delete
    // Delete a message locally.

    // POST /api/v1/instance/{instanceId}/deleteEverywhere
    // Delete a message for everyone.

    // POST /api/v1/instance/{instanceId}/getMessages
    // Get all messages.

    // POST /api/v1/instance/{instanceId}/mute
    // Mute a conversation.

    // POST /api/v1/instance/{instanceId}/sendConfirmationDelivered
    // Send a delivery confirmation for a message

    // POST /api/v1/instance/{instanceId}/sendConfirmationRead
    // Send a read confirmation for a message.

    // POST /api/v1/instance/{instanceId}/sendEphemeralConfirmationDelivered
    // Send a delivery confirmation for an ephemeral message.

    // POST /api/v1/instance/{instanceId}/sendEphemeralConfirmationRead
    // Send a read confirmation for an ephemeral message.

    // POST /api/v1/instance/{instanceId}/sendFile
    // Send a file to a conversation.

    // POST /api/v1/instance/{instanceId}/sendImage
    // Send an image to a conversation.

    // POST /api/v1/instance/{instanceId}/sendLocation
    // Send an location to a conversation.

    // POST /api/v1/instance/{instanceId}/sendPing
    // Send an ping to a conversation.

    // POST /api/v1/instance/{instanceId}/sendButtonAction
    // Send a button action to a poll.

    // POST /api/v1/instance/{instanceId}/sendButtonActionConfirmation
    // Send a confirmation to a button action.

    // POST /api/v1/instance/{instanceId}/sendReaction
    // Send a reaction to a message.

    // Send a text message to a conversation.
    @POST
    @Path("/instance/{id}/sendText")
    fun sendText(@PathParam("id") id: String): Instance {
        val instance = instanceService.getInstance(id)

        throw WebApplicationException("Not yet implemented")
    }

    // POST /api/v1/instance/{instanceId}/sendTyping
    // Send a typing indicator to a conversation.

    // POST /api/v1/instance/{instanceId}/updateText
    // Update a text message in a conversation.
}
