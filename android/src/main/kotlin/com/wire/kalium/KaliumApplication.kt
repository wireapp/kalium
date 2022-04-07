package com.wire.kalium

import android.app.Application
import androidx.work.Configuration
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.sync.WrapperWorkerFactory
import java.io.File

class KaliumApplication: Application(), Configuration.Provider {

    lateinit var coreLogic: CoreLogic

    override fun onCreate() {
        super.onCreate()

        val rootDir = File(this.filesDir, "accounts")
        coreLogic = CoreLogic(
            appContext = applicationContext,
            clientLabel = "kalium",
            rootPath = rootDir.absolutePath
        )
        CoreLogger.setLoggingLevel(
            level = KaliumLogLevel.DEBUG
        )
    }

    override fun getWorkManagerConfiguration(): Configuration {
        val myWorkerFactory = WrapperWorkerFactory(coreLogic)

        return Configuration.Builder()
            .setWorkerFactory(myWorkerFactory)
            .build()
    }

}
