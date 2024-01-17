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

import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.testservice.managed.CallRepository
import com.wire.kalium.testservice.managed.InstanceService
import com.wire.kalium.testservice.models.Call
import com.wire.kalium.testservice.models.CallRequest
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import javax.validation.Valid
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/api/v1/instance/{id}/call")
@Produces(MediaType.APPLICATION_JSON)
class CallResources(private val instanceService: InstanceService) {

    private val log = LoggerFactory.getLogger(CallResources::class.java.name)

    @POST
    @Path("/start")
    fun start(@PathParam("id") id: String, @Valid request: CallRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        val call = with(request) {
            runBlocking {
                CallRepository.start(instance, ConversationId(conversationId, conversationDomain), CallType.AUDIO)
            }
            Call(conversationId, conversationDomain, status = "STARTED")
        }
        return Response.ok(call).build()
    }

    @POST
    @Path("/startVideo")
    fun startVideo(@PathParam("instanceId") id: String, @Valid request: CallRequest): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        val call = with(request) {
            runBlocking {
                CallRepository.start(instance, ConversationId(conversationId, conversationDomain), CallType.VIDEO)
            }
        }
        return Response.ok(call).build()
    }

    @PUT
    @Path("/{callId}/stop")
    suspend fun stop(@PathParam("id") id: String, @PathParam("callId") callId: String): Response {
        val instance = instanceService.getInstanceOrThrow(id)
        CallRepository.stop(instance, callId)
        val call = Call(id = callId, status = "STOPPED")
        return Response.ok(call).build()
    }
}
