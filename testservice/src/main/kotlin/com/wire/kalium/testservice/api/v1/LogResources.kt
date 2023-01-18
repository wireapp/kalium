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
