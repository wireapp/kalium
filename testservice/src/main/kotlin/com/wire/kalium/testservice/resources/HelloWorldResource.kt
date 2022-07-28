package com.wire.kalium.testservice.resources

import com.codahale.metrics.annotation.Timed
import com.wire.kalium.testservice.api.Saying
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType

@Path("/hello-world")
@Produces(MediaType.APPLICATION_JSON)
class HelloWorldResource(private var template: String? = null, private var defaultName: String? = null) {
    private var counter: AtomicLong? = AtomicLong()

    @GET
    @Timed
    fun sayHello(@QueryParam("name") name: Optional<String?>): Saying? {
        val value = java.lang.String.format(template, name.orElse(defaultName))
        return Saying(counter!!.incrementAndGet(), value)
    }
}
