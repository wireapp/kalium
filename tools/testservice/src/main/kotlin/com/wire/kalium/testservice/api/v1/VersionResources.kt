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

import com.wire.kalium.testservice.models.VersionResponse
import io.swagger.v3.oas.annotations.Operation
import java.util.jar.Manifest
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
class VersionResources {
    @GET
    @Path("/version")
    @Operation(summary = "Get build commit hash")
    fun getVersion(): VersionResponse = VersionResponse(commit = readCommit())

    companion object {
        fun readCommit(): String {
            val stream = VersionResources::class.java.classLoader
                .getResourceAsStream("META-INF/MANIFEST.MF")
            return stream?.use { Manifest(it).mainAttributes.getValue("Git-Commit") } ?: "unknown"
        }
    }
}
