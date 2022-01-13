package com.wire.kalium.logic

import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeCommon
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationScope

actual class CoreLogic(clientLabel: String, rootProteusDirectoryPath: String) :
    CoreLogicCommon(clientLabel, rootProteusDirectoryPath) {
    override fun getAuthenticationScope(): AuthenticationScope {
        TODO("Not yet implemented")
    }

    override fun getSessionScope(session: AuthSession): UserSessionScope {
        TODO("Not yet implemented")
    }
}
