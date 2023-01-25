/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.testservice.models.Instance
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.MediaType

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
class ClientResources {

    // Set a user's availability.
    @POST
    @Path("/instance/{id}/availability")
    fun availability(@PathParam("id") id: String): Instance {
        throw WebApplicationException("Instance $id: Not yet implemented")
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
