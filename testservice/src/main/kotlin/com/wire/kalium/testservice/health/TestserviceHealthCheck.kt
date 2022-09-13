package com.wire.kalium.testservice.health

import com.codahale.metrics.health.HealthCheck
import com.wire.kalium.testservice.TestserviceConfiguration

class TestserviceHealthCheck : HealthCheck() {

    var template: String? = null

    fun CallBackendsHealthCheck(configuration: TestserviceConfiguration) {
        template = configuration.getTemplate()
    }

    @Throws(Exception::class)
    override fun check(): Result? {
        val saying = String.format(template.toString(), "TEST")
        return if (!saying.contains("TEST")) {
            return Result.unhealthy("template doesn't include a name")
        } else Result.healthy()
    }

}
