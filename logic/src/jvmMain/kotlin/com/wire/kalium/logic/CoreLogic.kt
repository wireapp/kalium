package com.wire.kalium.logic

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.configuration.ServerConfig

actual class CoreLogic(clientLabel: String, rootProteusDirectoryPath: String) :
    CoreLogicCommon(
        clientLabel = clientLabel,
        rootProteusDirectoryPath = rootProteusDirectoryPath,
        kaliumLoggerConfig = KaliumLogger.Config.DISABLED
    ) {
    override fun getAuthenticationScope(): AuthenticationScope {
        TODO("Not yet implemented")
    }

    override fun getSessionScope(session: AuthSession): UserSessionScope {
        TODO("Not yet implemented")
    }

}
