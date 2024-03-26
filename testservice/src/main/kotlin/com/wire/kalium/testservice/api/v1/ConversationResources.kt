/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.testservice.api.v1

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.testservice.managed.ConversationRepository
import com.wire.kalium.testservice.managed.InstanceService
import com.wire.kalium.testservice.models.ClearConversationRequest
import com.wire.kalium.testservice.models.DeleteMessageRequest
import com.wire.kalium.testservice.models.GetMessagesRequest
import com.wire.kalium.testservice.models.SendButtonActionConfirmationRequest
import com.wire.kalium.testservice.models.SendButtonActionRequest
import com.wire.kalium.testservice.models.SendConfirmationReadRequest
import com.wire.kalium.testservice.models.SendEphemeralConfirmationDeliveredRequest
import com.wire.kalium.testservice.models.SendFileRequest
import com.wire.kalium.testservice.models.SendImageRequest
import com.wire.kalium.testservice.models.SendLocationRequest
import com.wire.kalium.testservice.models.SendPingRequest
import com.wire.kalium.testservice.models.SendReactionRequest
import com.wire.kalium.testservice.models.SendTextRequest
import com.wire.kalium.testservice.models.SendTypingRequest
import com.wire.kalium.testservice.models.UpdateTextRequest
import io.swagger.v3.oas.annotations.Operation
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import javax.validation.Valid
import javax.ws.rs.Consumes
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import kotlin.streams.toList

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Suppress("TooManyFunctions")
class ConversationResources(private val instanceService: InstanceService) {

    private val log = LoggerFactory.getLogger(ConversationResources::class.java.name)

    @POST
    @Path("/instance/{id}/archive")
    @Operation(summary = "Archive a conversation")
    fun archive(@PathParam("id") id: String): Response {
        return Response.status(Response.Status.NOT_IMPLEMENTED).build()
    }

    @POST
    @Path("/instance/{id}/clear")
    @Operation(summary = "Clear a conversation")
    @Consumes(MediaType.APPLICATION_JSON)
    fun clear(@PathParam("id") id: String, @Valid request: ClearConversationRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        with(request) {
            runBlocking {
                ConversationRepository.clearConversation(instance, ConversationId(conversationId, conversationDomain))
            }
        }
        return Response.status(Response.Status.OK).build()
    }

    @POST
    @Path("/instance/{id}/delete")
    @Operation(summary = "Delete a message locally")
    @Consumes(MediaType.APPLICATION_JSON)
    fun delete(@PathParam("id") id: String, @Valid request: DeleteMessageRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        with(request) {
            runBlocking {
                ConversationRepository.deleteConversation(
                    instance,
                    ConversationId(conversationId, conversationDomain),
                    messageId,
                    false
                )
            }
        }
        return Response.status(Response.Status.OK).build()
    }

    @POST
    @Path("/instance/{id}/deleteEverywhere")
    @Operation(summary = "Delete a message for everyone")
    @Consumes(MediaType.APPLICATION_JSON)
    fun deleteEverywhere(@PathParam("id") id: String, @Valid request: DeleteMessageRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        return with(request) {
            runBlocking {
                ConversationRepository.deleteConversation(
                    instance,
                    ConversationId(conversationId, conversationDomain),
                    messageId,
                    true
                )
            }
        }
    }

    @POST
    @Path("/instance/{id}/getMessages")
    @Operation(summary = "Get all messages")
    @Consumes(MediaType.APPLICATION_JSON)
    fun getMessages(@PathParam("id") id: String, @Valid request: GetMessagesRequest): List<Message> {
        val instance = instanceService.getInstanceOrThrow(id)
        with(request) {
            return runBlocking {
                ConversationRepository.getMessages(
                    instance,
                    ConversationId(conversationId, conversationDomain)
                )
            }
        }
    }

    // POST /api/v1/instance/{instanceId}/mute
    // Mute a conversation.

    @POST
    @Path("/instance/{id}/sendConfirmationDelivered")
    @Operation(summary = "Send a delivery confirmation for a message")
    @Consumes(MediaType.APPLICATION_JSON)
    fun sendConfirmationDelivered(
        @PathParam("id") id: String,
        @Valid request: SendConfirmationReadRequest
    ): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        return with(request) {
            runBlocking {
                ConversationRepository.sendConfirmation(
                    instance,
                    ConversationId(conversationId, conversationDomain),
                    ReceiptType.DELIVERED,
                    firstMessageId
                )
            }
        }
    }

    @POST
    @Path("/instance/{id}/sendConfirmationRead")
    @Operation(summary = "Send a read confirmation for a message")
    @Consumes(MediaType.APPLICATION_JSON)
    fun sendConfirmationRead(
        @PathParam("id") id: String,
        @Valid request: SendConfirmationReadRequest
    ): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        return with(request) {
            runBlocking {
                ConversationRepository.sendConfirmation(
                    instance,
                    ConversationId(conversationId, conversationDomain),
                    ReceiptType.READ,
                    firstMessageId
                )
            }
        }
    }

    @POST
    @Path("/instance/{id}/sendEphemeralConfirmationDelivered")
    @Operation(summary = "Send a delivery confirmation for an ephemeral message")
    @Consumes(MediaType.APPLICATION_JSON)
    fun sendEphemeralConfirmationDelivered(
        @PathParam("id") id: String,
        @Valid request: SendEphemeralConfirmationDeliveredRequest
    ): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        return with(request) {
            runBlocking {
                ConversationRepository.sendConfirmation(
                    instance,
                    ConversationId(conversationId, conversationDomain),
                    ReceiptType.DELIVERED,
                    firstMessageId
                )
                moreMessageIds?.stream()?.forEach { messageId ->
                    runBlocking {
                        ConversationRepository.deleteConversation(
                            instance,
                            ConversationId(conversationId, conversationDomain),
                            messageId,
                            true
                        )
                    }
                }
                ConversationRepository.deleteConversation(
                    instance,
                    ConversationId(conversationId, conversationDomain),
                    firstMessageId,
                    true
                )
            }
        }
    }

    @POST
    @Path("/instance/{id}/sendFile")
    @Operation(summary = "Send a file to a conversation")
    @Consumes(MediaType.APPLICATION_JSON)
    fun sendFile(@PathParam("id") id: String, @Valid request: SendFileRequest): Response {
        log.info("Instance $id: Send file with name ${request.fileName}")
        val instance = instanceService.getInstanceOrThrow(id)
        return with(request) {
            runBlocking {
                ConversationRepository.sendFile(
                    instance,
                    ConversationId(conversationId, conversationDomain),
                    data,
                    fileName,
                    type,
                    messageTimer,
                    invalidHash,
                    otherAlgorithm,
                    otherHash
                )
            }
        }
    }

    @POST
    @Path("/instance/{id}/sendImage")
    @Operation(summary = "Send an image to a conversation")
    @Consumes(MediaType.APPLICATION_JSON)
    fun sendImage(@PathParam("id") id: String, @Valid sendImageRequest: SendImageRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        return with(sendImageRequest) {
            runBlocking {
                ConversationRepository.sendImage(
                    instance,
                    ConversationId(conversationId, conversationDomain),
                    data,
                    type,
                    width,
                    height,
                    messageTimer
                )
            }
        }
    }

    @POST
    @Path("/instance/{id}/sendLocation")
    @Operation(summary = "Send a location to a conversation")
    @Consumes(MediaType.APPLICATION_JSON)
    fun sendLocation(@PathParam("id") id: String, @Valid sendLocationRequest: SendLocationRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        return with(sendLocationRequest) {
            runBlocking {
                ConversationRepository.sendLocation(
                    instance,
                    ConversationId(conversationId, conversationDomain),
                    latitude,
                    longitude,
                    locationName,
                    zoom,
                    messageTimer
                )
            }
        }
    }

    @POST
    @Path("/instance/{id}/sendPing")
    @Operation(summary = "Send a ping to a conversation")
    @Consumes(MediaType.APPLICATION_JSON)
    fun sendPing(@PathParam("id") id: String, @Valid sendPingRequest: SendPingRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        return with(sendPingRequest) {
            runBlocking {
                ConversationRepository.sendPing(
                    instance,
                    ConversationId(conversationId, conversationDomain)
                )
            }
        }
    }

    @POST
    @Path("/instance/{id}/sendButtonAction")
    @Operation(summary = "Send a button action to a poll.")
    @Consumes(MediaType.APPLICATION_JSON)
    fun sendButtonActionConfirmation(@PathParam("id") id: String, @Valid request: SendButtonActionRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        return with(request) {
            runBlocking {
                ConversationRepository.sendButtonAction(
                    instance,
                    ConversationId(conversationId, conversationDomain),
                    referenceMessageId,
                    buttonId
                )
            }
        }
    }

    @POST
    @Path("/instance/{id}/sendButtonActionConfirmation")
    @Operation(summary = "Send a confirmation to a button action.")
    @Consumes(MediaType.APPLICATION_JSON)
    fun sendButtonActionConfirmation(@PathParam("id") id: String, @Valid request: SendButtonActionConfirmationRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        return with(request) {
            runBlocking {
                ConversationRepository.sendButtonActionConfirmation(
                    instance,
                    ConversationId(conversationId, conversationDomain),
                    referenceMessageId,
                    buttonId,
                    userIds.map { UserId(it, conversationDomain) }
                )
            }
        }
    }

    @POST
    @Path("/instance/{id}/sendReaction")
    @Operation(summary = "Send a reaction to a message")
    @Consumes(MediaType.APPLICATION_JSON)
    fun sendReaction(@PathParam("id") id: String, @Valid sendReactionRequest: SendReactionRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        return with(sendReactionRequest) {
            runBlocking {
                ConversationRepository.sendReaction(
                    instance,
                    ConversationId(conversationId, conversationDomain),
                    originalMessageId,
                    type
                )
            }
        }
    }

    @POST
    @Path("/instance/{id}/sendText")
    @Operation(
        summary = "Send a text message to a conversation",
        description = "This can include mentions and reply (buttons and link previews not yet implemented)"
    )
    @Consumes(MediaType.APPLICATION_JSON)
    fun sendText(@PathParam("id") id: String, @Valid sendTextRequest: SendTextRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        // TODO Implement link previews here
        val quotedMessageId = sendTextRequest.quote?.quotedMessageId
        val mentions = when (sendTextRequest.mentions.size) {
            0 -> emptyList<MessageMention>()
            else -> {
                sendTextRequest.mentions.stream().map { mention ->
                    MessageMention(
                        mention.start,
                        mention.length,
                        UserId(mention.userId, mention.userDomain),
                        false
                    )
                }.toList()
            }
        }
        return with(sendTextRequest) {
            runBlocking {
                ConversationRepository.sendTextMessage(
                    instance,
                    ConversationId(conversationId, conversationDomain),
                    text,
                    mentions,
                    messageTimer,
                    quotedMessageId,
                    buttons
                )
            }
        }
    }

    @POST
    @Path("/instance/{id}/updateText")
    @Operation(summary = "Update a text message in a conversation")
    @Consumes(MediaType.APPLICATION_JSON)
    fun updateText(@PathParam("id") id: String, @Valid updateTextRequest: UpdateTextRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        // TODO Implement buttons and link previews here
        val mentions = when (updateTextRequest.mentions.size) {
            0 -> emptyList<MessageMention>()
            else -> {
                updateTextRequest.mentions.stream().map { mention ->
                    MessageMention(
                        mention.start,
                        mention.length,
                        UserId(mention.userId, mention.userDomain),
                        false
                    )
                }.toList()
            }
        }
        return with(updateTextRequest) {
            runBlocking {
                ConversationRepository.updateTextMessage(
                    instance,
                    ConversationId(conversationId, conversationDomain),
                    text,
                    mentions,
                    firstMessageId,
                    messageTimer
                )
            }
        }
    }

    @POST
    @Path("/instance/{id}/sendTyping")
    @Operation(summary = "Send a typing indicator to a conversation")
    @Consumes(MediaType.APPLICATION_JSON)
    fun sendTyping(@PathParam("id") id: String, @Valid request: SendTypingRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        return with(request) {
            runBlocking {
                ConversationRepository.sendTyping(
                    instance,
                    ConversationId(conversationId, conversationDomain),
                    status
                )
            }
        }
    }
}
