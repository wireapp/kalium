package com.wire.kalium.testservice

import com.wire.kalium.testservice.resources.HelloWorldResource
import io.dropwizard.Application
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment


class TestserviceApplication: Application<TestserviceConfiguration>() {

    companion object {
        @JvmStatic fun main(args: Array<String>) = TestserviceApplication().run(*args)
    }

    override fun getName(): String? {
        return "hello-world"
    }

    override fun initialize(bootstrap: Bootstrap<TestserviceConfiguration?>?) {
        // nothing to do yet
    }

    override fun run(configuration: TestserviceConfiguration, environment: Environment) {
        val resource = HelloWorldResource(configuration.getTemplate(), configuration.getDefaultName())
        environment.jersey().register(resource)
    }

}
