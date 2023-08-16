/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic

import co.touchlab.kermit.LogWriter
import com.wire.kalium.cryptography.CryptographyLogger
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.NetworkUtilLogger
import com.wire.kalium.persistence.PersistenceLogger

internal var kaliumLogger = KaliumLogger.disabled()
internal var callingLogger = KaliumLogger.disabled()

object CoreLogger {
    fun setLoggingLevel(level: KaliumLogLevel, vararg logWriters: LogWriter = arrayOf()) {
        kaliumLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "CoreLogic"
            ),
            logWriters = logWriters
        )

        callingLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "Calling"
            ),
            logWriters = logWriters
        )

        NetworkUtilLogger.setLoggingLevel(level = level, logWriters = logWriters)
        CryptographyLogger.setLoggingLevel(level = level, logWriters = logWriters)
        PersistenceLogger.setLoggingLevel(level = level, logWriters = logWriters)
    }
}
