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

import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.Configuration
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration

class TestserviceConfiguration(
    private var saveToUserHomeDirectory: Boolean = true,
    private var instanceCreationTimeoutInSeconds: Long = 60,
    private var instanceMaximumRuntimeInMinutes: Long = 10,
    private var instanceCleanupPeriodInMinutes: Long = 5
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

    @JsonProperty
    fun getInstanceMaximumRuntimeInMinutes(): Long {
        return instanceMaximumRuntimeInMinutes
    }

    @JsonProperty
    fun setInstanceMaximumRuntimeInMinutes(runtime: Long) {
        instanceMaximumRuntimeInMinutes = runtime
    }

    @JsonProperty
    fun getInstanceCleanupPeriodInMinutes(): Long {
        return instanceCleanupPeriodInMinutes
    }

    @JsonProperty
    fun setInstanceCleanupPeriodInMinutes(period: Long) {
        instanceCleanupPeriodInMinutes = period
    }
}
