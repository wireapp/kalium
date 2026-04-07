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

import com.wire.kalium.testservice.managed.InstanceService
import com.wire.kalium.testservice.models.E2EIFinalizeRequest
import com.wire.kalium.testservice.models.E2EIFinalizeResponse
import com.wire.kalium.testservice.models.E2EIInitializationResponse
import io.swagger.v3.oas.annotations.Operation
import kotlinx.coroutines.runBlocking
import javax.validation.Valid
import javax.ws.rs.Consumes
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
class E2EIResources(
    private val instanceService: InstanceService
) {

    @POST
    @Path("/instance/{id}/e2ei/init")
    @Operation(summary = "Initialize E2EI enrollment for an instance")
    fun initialize(@PathParam("id") id: String): E2EIInitializationResponse = runBlocking {
        instanceService.initializeE2EI(id)
    }

    @POST
    @Path("/instance/{id}/e2ei/finalize")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Finalize E2EI enrollment for an instance")
    fun finalize(
        @PathParam("id") id: String,
        @Valid request: E2EIFinalizeRequest
    ): E2EIFinalizeResponse = runBlocking {
        instanceService.finalizeE2EI(id, request)
    }
}
