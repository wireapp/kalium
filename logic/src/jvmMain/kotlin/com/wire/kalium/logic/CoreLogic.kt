package com.wire.kalium.logic

import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.network_config.BackendType

actual class CoreLogic(clientLabel: String, rootProteusDirectoryPath: String, backEndType: BackendType) :
    CoreLogicCommon(clientLabel, rootProteusDirectoryPath, backEndType) {
    override fun getAuthenticationScope(): AuthenticationScope {
        TODO("Not yet implemented")
    }

    override fun getSessionScope(session: AuthSession): UserSessionScope {
        TODO("Not yet implemented")
    }
}
