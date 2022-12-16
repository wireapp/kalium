package com.wire.kalium.logic

import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.featureFlags.KaliumConfigs


fun loginSample() {
    val coreLogic = CoreLogic("SomeLabel", "rootPath", KaliumConfigs())
    coreLogic.authenticationScope(
        ServerConfig(
            "ID?",
            ServerConfig.PRODUCTION,
            ServerConfig.MetaData(false, CommonApiVersionType.Unknown, "")
        )
    ) {

    }
}
