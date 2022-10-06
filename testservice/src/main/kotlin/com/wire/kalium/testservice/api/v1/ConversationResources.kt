package com.wire.kalium.testservice.api.v1

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.testservice.managed.ConversationRepository
import com.wire.kalium.testservice.managed.InstanceService
import com.wire.kalium.testservice.models.DeleteMessageRequest
import com.wire.kalium.testservice.models.GetMessagesRequest
import com.wire.kalium.testservice.models.SendFileRequest
import com.wire.kalium.testservice.models.SendImageRequest
import com.wire.kalium.testservice.models.SendPingRequest
import com.wire.kalium.testservice.models.SendReactionRequest
import com.wire.kalium.testservice.models.SendTextRequest
import com.wire.kalium.testservice.models.SendTextResponse
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.slf4j.LoggerFactory
import javax.validation.Valid
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Api
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
class ConversationResources(private val instanceService: InstanceService) {

    private val log = LoggerFactory.getLogger(ConversationResources::class.java.name)

    // archive a conversation
    /*
    @POST
    @Path("/instance/{id}/archive")
    fun archive(@PathParam("id") id: String): Instance {
        throw WebApplicationException("Not yet implemented")
    }
    */

    // clear a conversation
    /*
    @POST
    @Path("/instance/{id}/clear")
    fun clear(@PathParam("id") id: String): Instance {
        throw WebApplicationException("Not yet implemented")
    }
    */

    @POST
    @Path("/instance/{id}/delete")
    @ApiOperation(value = "Delete a message locally")
    fun delete(@PathParam("id") id: String, @Valid deleteMessageRequest: DeleteMessageRequest) {
        val instance = instanceService.getInstanceOrThrow(id)
        with(deleteMessageRequest) {
            ConversationRepository.deleteConversation(
                instance,
                ConversationId(conversationId, conversationDomain),
                messageId,
                false
            )
        }
    }

    @POST
    @Path("/instance/{id}/deleteEverywhere")
    @ApiOperation(value = "Delete a message for everyone")
    fun deleteEverywhere(@PathParam("id") id: String, @Valid deleteMessageRequest: DeleteMessageRequest) {
        val instance = instanceService.getInstanceOrThrow(id)
        with(deleteMessageRequest) {
            ConversationRepository.deleteConversation(
                instance,
                ConversationId(conversationId, conversationDomain),
                messageId,
                true
            )
        }
    }

    @POST
    @Path("/instance/{id}/getMessages")
    @ApiOperation(value = "Get all messages")
    fun getMessages(@PathParam("id") id: String, @Valid getMessagesRequest: GetMessagesRequest): List<Message> {
        val instance = instanceService.getInstanceOrThrow(id)
        with(getMessagesRequest) {
            return ConversationRepository.getMessages(
                instance,
                ConversationId(conversationId, conversationDomain)
            )
        }
    }

    // POST /api/v1/instance/{instanceId}/mute
    // Mute a conversation.

    // POST /api/v1/instance/{instanceId}/sendConfirmationDelivered
    // Send a delivery confirmation for a message

    // POST /api/v1/instance/{instanceId}/sendConfirmationRead
    // Send a read confirmation for a message.
    // Used in: web-mls

    // POST /api/v1/instance/{instanceId}/sendEphemeralConfirmationDelivered
    // Send a delivery confirmation for an ephemeral message.

    // POST /api/v1/instance/{instanceId}/sendEphemeralConfirmationRead
    // Send a read confirmation for an ephemeral message.

    @POST
    @Path("/instance/{id}/sendFile")
    @ApiOperation(value = "Send a file to a conversation")
    fun sendFile(@PathParam("id") id: String, @Valid sendFileRequest: SendFileRequest): Response {
        log.info("Instance $id: Send file with name ${sendFileRequest.fileName}")
        val instance = instanceService.getInstanceOrThrow(id)
        with(sendFileRequest) {
            ConversationRepository.sendFile(
                instance,
                ConversationId(conversationId, conversationDomain),
                data,
                fileName,
                type,
                invalidHash,
                otherAlgorithm,
                otherHash
            )
        }
        return Response.status(Response.Status.OK).build()
    }

    @POST
    @Path("/instance/{id}/sendImage")
    @ApiOperation(value = "Send an image to a conversation")
    fun sendImage(@PathParam("id") id: String, @Valid sendImageRequest: SendImageRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        with(sendImageRequest) {
            ConversationRepository.sendImage(
                instance,
                ConversationId(conversationId, conversationDomain),
                data,
                type,
                height,
                width
            )
        }
        return Response.status(Response.Status.OK).build()
    }

    // POST /api/v1/instance/{instanceId}/sendLocation
    // Send an location to a conversation.

    @POST
    @Path("/instance/{id}/sendPing")
    @ApiOperation(value = "Send a ping to a conversation")
    fun sendPing(@PathParam("id") id: String, @Valid sendPingRequest: SendPingRequest) {
        val instance = instanceService.getInstanceOrThrow(id)
        with(sendPingRequest) {
            ConversationRepository.sendPing(
                instance,
                ConversationId(conversationId, conversationDomain)
            )
        }
    }

    // POST /api/v1/instance/{instanceId}/sendButtonAction
    // Send a button action to a poll.

    // POST /api/v1/instance/{instanceId}/sendButtonActionConfirmation
    // Send a confirmation to a button action.

    @POST
    @Path("/instance/{id}/sendReaction")
    @ApiOperation(
        value = "Send a reaction to a message"
    )
    fun sendReaction(@PathParam("id") id: String, @Valid sendReactionRequest: SendReactionRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        // TODO Implement mentions, reply and ephemeral messages here
        with(sendReactionRequest) {
            ConversationRepository.sendReaction(
                instance,
                ConversationId(conversationId, conversationDomain),
                originalMessageId,
                type
            )
        }
        return Response.status(Response.Status.OK).entity(SendTextResponse(id, "", "")).build()
    }

    @POST
    @Path("/instance/{id}/sendText")
    @ApiOperation(
        value = "Send a text message to a conversation (can include mentions, reply, buttons and link previews)"
    )
    fun sendText(@PathParam("id") id: String, @Valid sendTextRequest: SendTextRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        // TODO Implement mentions, reply and ephemeral messages here
        with(sendTextRequest) {
            ConversationRepository.sendTextMessage(
                instance,
                ConversationId(conversationId, conversationDomain),
                text
            )
        }
        return Response.status(Response.Status.OK).entity(SendTextResponse(id, "", "")).build()
    }

    // POST /api/v1/instance/{instanceId}/sendTyping
    // Send a typing indicator to a conversation.

    // POST /api/v1/instance/{instanceId}/updateText
    // Update a text message in a conversation.
}
