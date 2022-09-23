package com.wire.kalium

import android.app.Application
import androidx.work.Configuration
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.ForegroundNotificationDetailsProvider
import com.wire.kalium.logic.sync.WrapperWorkerFactory
import java.io.File

class KaliumApplication : Application(), Configuration.Provider {

    lateinit var coreLogic: CoreLogic

    override fun onCreate() {
        super.onCreate()

        val rootDir = File(this.filesDir, "accounts")
        coreLogic = CoreLogic(
            appContext = applicationContext,
            clientLabel = "kalium",
            rootPath = rootDir.absolutePath,
            kaliumConfigs = KaliumConfigs()
        )
        CoreLogger.setLoggingLevel(
            level = KaliumLogLevel.DEBUG
        )
    }

    override fun getWorkManagerConfiguration(): Configuration {
        val myWorkerFactory = WrapperWorkerFactory(coreLogic, object : ForegroundNotificationDetailsProvider {
            override fun getSmallIconResId(): Int = R.drawable.ic_launcher_foreground
        })

        return Configuration.Builder()
            .setWorkerFactory(myWorkerFactory)
            .build()
    }

}
