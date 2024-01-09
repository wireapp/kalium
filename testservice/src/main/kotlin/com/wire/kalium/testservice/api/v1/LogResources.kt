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
import io.swagger.v3.oas.annotations.Operation
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Path("/")
@Produces(MediaType.TEXT_PLAIN)
class LogResources(
    private val configuration: TestserviceConfiguration
) {
    @GET
    @Path("/application.log")
    @Operation(summary = "Get application log of testservice")
    fun getLogs(): String {
        return Files.readString(File("/var/log/kalium-testservice/application.log").toPath(), Charset.defaultCharset())
    }
}
