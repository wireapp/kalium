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

package com.wire.kalium.testservice

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jmx.JmxReporter
import com.wire.kalium.testservice.api.v1.ClientResources
import com.wire.kalium.testservice.api.v1.ConversationResources
import com.wire.kalium.testservice.api.v1.InstanceLifecycle
import com.wire.kalium.testservice.api.v1.LogResources
import com.wire.kalium.testservice.health.TestserviceHealthCheck
import com.wire.kalium.testservice.managed.InstanceService
import io.dropwizard.Application
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import io.federecio.dropwizard.swagger.SwaggerBundle
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.dropwizard.DropwizardExports
import io.prometheus.client.exporter.MetricsServlet
import org.eclipse.jetty.servlet.ServletHolder
import org.slf4j.LoggerFactory

class TestserviceApplication : Application<TestserviceConfiguration>() {

    private val log = LoggerFactory.getLogger(TestserviceApplication::class.java.name)

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

        log.info("Creating cleanup worker pool...")
        val cleanupPool = environment.lifecycle().scheduledExecutorService(name, true)
            .threads(2)
            .removeOnCancelPolicy(true)
            .build()

        // metrics containing only application related stuff (no memory, jvm, etc)
        val metricRegistry = MetricRegistry()

        // managed
        val instanceService = InstanceService(metricRegistry, cleanupPool, configuration)
        environment.lifecycle().manage(instanceService)

        // metrics
        CollectorRegistry.defaultRegistry.register(DropwizardExports(metricRegistry))
        environment.applicationContext.servletHandler.addServletWithMapping(
            ServletHolder(MetricsServlet()),
            "/prometheus"
        )

        // resources
        val clientResources = ClientResources(instanceService)
        val conversationResources = ConversationResources(instanceService)
        val instanceLifecycle = InstanceLifecycle(instanceService, configuration)
        val logResources = LogResources(configuration)
        environment.healthChecks().register("template", TestserviceHealthCheck(configuration))
        // returns better error messages on JSON issues
        environment.jersey().register(JsonProcessingExceptionMapper(true))
        environment.jersey().register(clientResources)
        environment.jersey().register(conversationResources)
        environment.jersey().register(instanceLifecycle)
        environment.jersey().register(logResources)

        // metrics
        val jmxReporter = JmxReporter.forRegistry(environment.metrics()).build()
        jmxReporter.start()
    }

}
