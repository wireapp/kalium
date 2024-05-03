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

import com.wire.kalium.testservice.TestserviceConfiguration
import com.wire.kalium.testservice.managed.InstanceService
import com.wire.kalium.testservice.models.Instance
import com.wire.kalium.testservice.models.InstanceRequest
import io.swagger.v3.oas.annotations.Operation
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.validation.Valid
import javax.ws.rs.Consumes
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.WebApplicationException
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.ConnectionCallback
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
class InstanceLifecycle(
    private val instanceService: InstanceService,
    private val configuration: TestserviceConfiguration
) {

    private val log = LoggerFactory.getLogger(InstanceLifecycle::class.java.name)

    @GET
    @Path("/instances")
    @Operation(summary = "Get all currently running instances")
    fun getInstances(): Collection<Instance> {
        return instanceService.getInstances()
    }

    @PUT
    @Path("/instance")
    @Operation(summary = "Create a new instance")
    @Consumes(MediaType.APPLICATION_JSON)
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun createInstance(@Valid instanceRequest: InstanceRequest, @Suspended ar: AsyncResponse) {
        val instanceId = UUID.randomUUID().toString()
        val timeout = configuration.getInstanceCreationTimeoutInSeconds()

        // handles unresponsive instances
        ar.setTimeout(timeout, TimeUnit.SECONDS)
        ar.setTimeoutHandler { asyncResponse: AsyncResponse ->
            log.error("Instance $instanceId: Async create instance request timed out after $timeout seconds")
            asyncResponse.resume(
                Response
                    .status(Response.Status.GATEWAY_TIMEOUT)
                    .entity("Instance $instanceId: Async create instance request timed out after $timeout seconds")
                    .build()
            )
            if (instanceService.getInstance(instanceId) != null) {
                instanceService.deleteInstance(instanceId)
            }
        }
        // handles client disconnect
        ar.register(ConnectionCallback { disconnected: AsyncResponse? ->
            log.error("Instance $instanceId: Client disconnected from async create instance request")
            if (instanceService.getInstance(instanceId) != null) {
                instanceService.deleteInstance(instanceId)
            }
        })

        val createdInstance = try {
            runBlocking {
                instanceService.createInstance(instanceId, instanceRequest)
            }
        } catch (we: WebApplicationException) {
            throw we
        } catch (e: Exception) {
            log.error("Instance $instanceId: Could not create instance: " + e.message, e)
            throw WebApplicationException("Could not create instance: " + e.message)
        }

        ar.resume(createdInstance)
    }

    @GET
    @Path("/instance/{id}")
    @Operation(summary = "Get information about an instance")
    fun getInstance(@PathParam("id") id: String): Instance {
        val instance = instanceService.getInstance(id)
        return instance ?: throw WebApplicationException("No instance found with id $id")
    }

    @DELETE
    @Path("/instance/{id}")
    @Operation(summary = "Delete an instance")
    fun deleteInstance(@PathParam("id") id: String) {
        instanceService.getInstance(id) ?: throw WebApplicationException("No instance found with id $id")
        instanceService.deleteInstance(id)
    }

}
