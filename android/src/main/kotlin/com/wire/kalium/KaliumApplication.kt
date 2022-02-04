package com.wire.kalium

import android.app.Application
import androidx.work.*
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.sync.WrapperWorkerFactory
import java.io.File

class KaliumApplication: Application(), Configuration.Provider {

    lateinit var coreLogic: CoreLogic

    override fun onCreate() {
        super.onCreate()

        val rootProteusDir = File(this.filesDir, "proteus")
        coreLogic = CoreLogic(applicationContext, "kalium", rootProteusDir.absolutePath)
    }

    override fun getWorkManagerConfiguration(): Configuration {
        val myWorkerFactory = WrapperWorkerFactory(coreLogic)

        return Configuration.Builder()
            .setWorkerFactory(myWorkerFactory)
            .build()
    }

}
