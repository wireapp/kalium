/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.common.logger

import com.wire.kalium.cryptography.CryptographyLogger
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.NetworkLogger
import com.wire.kalium.network.NetworkUtilLogger
import com.wire.kalium.network.utils.NetworkModelLogger
import com.wire.kalium.persistence.PersistenceLogger

object CoreLogger {
    private var kaliumLoggerConfig = KaliumLogger.Config.DISABLED

    fun init(config: KaliumLogger.Config) {
        kaliumLoggerConfig = config
        kaliumLogger = KaliumLogger(config = kaliumLoggerConfig, tag = "CoreLogic")
        callingLogger = KaliumLogger(config = kaliumLoggerConfig, tag = "Calling")
        NetworkLogger.init(config = config)
        NetworkModelLogger.init(config = config)
        NetworkUtilLogger.init(config = config)
        CryptographyLogger.init(config = config)
        PersistenceLogger.init(config = config)
    }

    fun setLoggingLevel(level: KaliumLogLevel) {
        kaliumLoggerConfig.logLevel = level
    }
}
