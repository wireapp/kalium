package com.wire.kalium.testservice.api.v1

import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.feature.session.CurrentSessionResult
import com.wire.kalium.testservice.managed.InstanceService
import com.wire.kalium.testservice.models.GetMessagesRequest
import com.wire.kalium.testservice.models.Instance
import com.wire.kalium.testservice.models.SendTextRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import javax.validation.Valid
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.MediaType

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

    // POST /api/v1/instance/{instanceId}/delete
    // Delete a message locally.

    // POST /api/v1/instance/{instanceId}/deleteEverywhere
    // Delete a message for everyone.

    // Get all messages.
    @POST
    @Path("/instance/{id}/getMessages")
    fun getMessages(@PathParam("id") id: String, @Valid getMessagesRequest: GetMessagesRequest): List<Message> {
        val instance = instanceService.getInstance(id)
        if (instance != null) {
            instance.coreLogic?.globalScope {
                val result = session.currentSession()
                if (result is CurrentSessionResult.Success) {
                    instance.coreLogic.sessionScope(result.authSession.session.userId) {
                        val recentMessages = runBlocking {
                            with(getMessagesRequest) {
                                log.info("Instance $id: Get recent messages...")
                                messages.getRecentMessages(ConversationId(conversationId, conversationDomain)).first()
                            }
                        }
                        return recentMessages
                    }
                }
            }
        } else {
            throw WebApplicationException("Instance $id: Instance not found or already destroyed")
        }
        throw WebApplicationException("Instance $id: Could not get recent messages")
    }

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
    fun sendText(@PathParam("id") id: String, @Valid sendTextRequest: SendTextRequest) {
        val instance = instanceService.getInstance(id)
        instance?.coreLogic?.globalScope {
            val result = session.currentSession()
            if (result is CurrentSessionResult.Success) {
                instance.coreLogic.sessionScope(result.authSession.session.userId) {
                    sendTextRequest.text?.let {
                        log.info("Instance ${id}: Send text message '${sendTextRequest.text}'")
                        runBlocking {
                            with(sendTextRequest) {
                                messages.sendTextMessage(
                                    ConversationId(conversationId, conversationDomain), sendTextRequest.text
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // POST /api/v1/instance/{instanceId}/sendTyping
    // Send a typing indicator to a conversation.

    // POST /api/v1/instance/{instanceId}/updateText
    // Update a text message in a conversation.
}
