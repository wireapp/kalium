package com.wire.kalium.logic

import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.configuration.BuildType

actual class CoreLogic(clientLabel: String, rootProteusDirectoryPath: String, buildType: BuildType) :
    CoreLogicCommon(clientLabel, rootProteusDirectoryPath, buildType) {
    override fun getAuthenticationScope(): AuthenticationScope {
        TODO("Not yet implemented")
    }

    override fun getSessionScope(session: AuthSession): UserSessionScope {
        TODO("Not yet implemented")
    }
}
