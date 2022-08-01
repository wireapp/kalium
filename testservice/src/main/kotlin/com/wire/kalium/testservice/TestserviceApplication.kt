package com.wire.kalium.testservice

import com.wire.kalium.testservice.api.v1.ClientResources
import com.wire.kalium.testservice.api.v1.ConversationResources
import com.wire.kalium.testservice.api.v1.InstanceLifecycle
import com.wire.kalium.testservice.health.TestserviceHealthCheck
import com.wire.kalium.testservice.managed.InstanceService
import io.dropwizard.Application
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import io.federecio.dropwizard.swagger.SwaggerBundle
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration


class TestserviceApplication : Application<TestserviceConfiguration>() {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = TestserviceApplication().run(*args)
    }

    override fun getName(): String? {
        return "hello-world"
    }

    override fun initialize(bootstrap: Bootstrap<TestserviceConfiguration?>?) {
        bootstrap!!.addBundle(object : SwaggerBundle<TestserviceConfiguration>() {
            override fun getSwaggerBundleConfiguration(configuration: TestserviceConfiguration): SwaggerBundleConfiguration? {
                return configuration.swaggerBundleConfiguration
            }
        })
    }

    override fun run(configuration: TestserviceConfiguration, environment: Environment) {

        // managed
        val instanceService = InstanceService()
        environment.lifecycle().manage(instanceService)

        // resources
        val clientResources = ClientResources()
        val conversationResources = ConversationResources()
        val instanceLifecycle = InstanceLifecycle(instanceService)
        environment.healthChecks().register("template", TestserviceHealthCheck())
        environment.jersey().register(clientResources)
        environment.jersey().register(conversationResources)
        environment.jersey().register(instanceLifecycle)
    }

}
