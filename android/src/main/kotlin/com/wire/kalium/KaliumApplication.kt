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

package com.wire.kalium

import android.app.Application
import androidx.work.Configuration
import com.wire.kalium.android.R
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.WrapperWorkerFactory
import java.io.File

class KaliumApplication : Application(), Configuration.Provider {

    lateinit var coreLogic: CoreLogic

    override fun onCreate() {
        super.onCreate()

        val rootDir = File(this.filesDir, "accounts")
        coreLogic = CoreLogic(
            appContext = applicationContext,
            rootPath = rootDir.absolutePath,
            kaliumConfigs = KaliumConfigs(),
            userAgent = "Kalium/android/testApp"
        )
        CoreLogger.setLoggingLevel(
            level = KaliumLogLevel.DEBUG
        )
    }

    override val workManagerConfiguration: Configuration
        get() {
            val myWorkerFactory = WrapperWorkerFactory(coreLogic) { R.drawable.ic_launcher_foreground }
            return Configuration.Builder()
                .setWorkerFactory(myWorkerFactory)
                .build()
        }

}
