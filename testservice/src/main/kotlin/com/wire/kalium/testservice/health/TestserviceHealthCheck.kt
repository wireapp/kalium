package com.wire.kalium.testservice.health

import com.codahale.metrics.health.HealthCheck
import com.wire.kalium.testservice.TestserviceConfiguration
import java.io.File

class TestserviceHealthCheck(configuration: TestserviceConfiguration) : HealthCheck() {

    var saveInUserHomeDirectory: Boolean = false

    init {
        saveInUserHomeDirectory = configuration.getSaveToUserHomeDirectory()
    }

    @Throws(Exception::class)
    override fun check(): Result? {
        if (saveInUserHomeDirectory) {
            val savePath = File("${System.getProperty("user.home")}/.testservice/")
            if (savePath.exists() && savePath.isDirectory) {
                return Result.healthy()
            } else {
                return Result.unhealthy("$savePath is not a directory or does not exist")
            }
        }
        return Result.healthy()
    }

}
