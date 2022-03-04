package com.wire.kalium

import android.app.Application
import androidx.work.*
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.sync.WrapperWorkerFactory
import java.io.File

class KaliumApplication: Application(), Configuration.Provider {

    lateinit var coreLogic: CoreLogic

    override fun onCreate() {
        super.onCreate()

        val rootProteusDir = File(this.filesDir, "proteus")
        coreLogic = CoreLogic(
            applicationContext = applicationContext,
            clientLabel = "kalium",
            rootProteusDirectoryPath = rootProteusDir.absolutePath,
            kaliumLoggerConfig = KaliumLogger.Config.DISABLED
        )
    }

    override fun getWorkManagerConfiguration(): Configuration {
        val myWorkerFactory = WrapperWorkerFactory(coreLogic)

        return Configuration.Builder()
            .setWorkerFactory(myWorkerFactory)
            .build()
    }

}
