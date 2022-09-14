package com.wire.kalium.testservice

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jmx.JmxReporter
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
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.dropwizard.DropwizardExports
import io.prometheus.client.exporter.MetricsServlet
import org.eclipse.jetty.servlet.ServletHolder

class TestserviceApplication : Application<TestserviceConfiguration>() {

    companion object {
        @JvmStatic
        @Suppress("SpreadOperator")
        fun main(args: Array<String>) = TestserviceApplication().run(*args)
    }

    override fun getName(): String {
        return "kalium-testservice"
    }

    override fun initialize(bootstrap: Bootstrap<TestserviceConfiguration?>?) {
        bootstrap!!.addBundle(object : SwaggerBundle<TestserviceConfiguration>() {
            override fun getSwaggerBundleConfiguration(
                configuration: TestserviceConfiguration
            ): SwaggerBundleConfiguration? {
                return configuration.swaggerBundleConfiguration
            }
        })
    }

    override fun run(configuration: TestserviceConfiguration, environment: Environment) {

        // metrics containing only application related stuff (no memory, jvm, etc)
        val metricRegistry = MetricRegistry()

        // managed
        val instanceService = InstanceService(metricRegistry)
        environment.lifecycle().manage(instanceService)

        // metrics
        CollectorRegistry.defaultRegistry.register(DropwizardExports(metricRegistry))
        environment.applicationContext.servletHandler.addServletWithMapping(
            ServletHolder(MetricsServlet()),
            "/prometheus"
        )

        // resources
        val clientResources = ClientResources()
        val conversationResources = ConversationResources(instanceService)
        val instanceLifecycle = InstanceLifecycle(instanceService, configuration)
        environment.healthChecks().register("template", TestserviceHealthCheck(configuration))
        environment.jersey().register(clientResources)
        environment.jersey().register(conversationResources)
        environment.jersey().register(instanceLifecycle)

        // metrics
        val jmxReporter = JmxReporter.forRegistry(environment.metrics()).build()
        jmxReporter.start()
    }

}
