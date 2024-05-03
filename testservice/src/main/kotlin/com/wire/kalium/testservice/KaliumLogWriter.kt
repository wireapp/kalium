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

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.wire.kalium.testservice.managed.InstanceService
import org.slf4j.LoggerFactory

class KaliumLogWriter(private val instanceId: String) : LogWriter() {

    private val log = LoggerFactory.getLogger(InstanceService::class.java.name)

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        when (severity) {
            Severity.Verbose -> log.debug("Instance $instanceId: $message")
            Severity.Debug -> log.debug("Instance $instanceId: $message")
            Severity.Info -> log.info("Instance $instanceId: $message")
            Severity.Warn -> log.warn("Instance $instanceId: $message")
            Severity.Error -> log.error("Instance $instanceId: $message $throwable")
            Severity.Assert -> log.info("Instance $instanceId: $message")
        }
    }
}
