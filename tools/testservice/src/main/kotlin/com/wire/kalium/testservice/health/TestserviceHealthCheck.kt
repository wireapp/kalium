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
            if (!savePath.exists() || !savePath.isDirectory) {
                return Result.unhealthy("$savePath is not a directory or does not exist")
            }
        }
        return Result.healthy()
    }

}
