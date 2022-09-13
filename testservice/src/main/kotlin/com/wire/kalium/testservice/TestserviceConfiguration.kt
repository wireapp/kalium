package com.wire.kalium.testservice

import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.Configuration
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration

class TestserviceConfiguration(
    private var saveToUserHomeDirectory: Boolean = true,
    private var instanceCreationTimeoutInSeconds: Long = 60
) : Configuration() {

    @JsonProperty("swagger")
    var swaggerBundleConfiguration: SwaggerBundleConfiguration? = null

    @JsonProperty
    fun getSaveToUserHomeDirectory(): Boolean {
        return saveToUserHomeDirectory
    }

    @JsonProperty
    fun getInstanceCreationTimeoutInSeconds(): Long {
        return instanceCreationTimeoutInSeconds
    }

    @JsonProperty
    fun setInstanceCreationTimeoutInSeconds(timeout: Long) {
        instanceCreationTimeoutInSeconds = timeout
    }

}
