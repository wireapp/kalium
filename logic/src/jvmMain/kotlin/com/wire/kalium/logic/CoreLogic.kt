package com.wire.kalium.logic

import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.feature.UserSessionScopeCommon
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationScope

actual class CoreLogic(clientLabel: String, rootProteusDirectoryPath: String) :
    CoreLogicCommon(clientLabel, rootProteusDirectoryPath) {
    override fun getAuthenticationScope(): AuthenticationScope {
        TODO("Not yet implemented")
    }

    override val clientConfig: ClientConfig
        get() = ClientConfig()

    override fun getSessionScope(session: AuthSession): UserSessionScopeCommon {
        TODO("Not yet implemented")
    }
}
