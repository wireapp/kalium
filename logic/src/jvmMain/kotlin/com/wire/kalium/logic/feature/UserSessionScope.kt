package com.wire.kalium.logic.feature

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig

actual class UserSessionScope(
    authenticatedDataSourceSet: AuthenticatedDataSourceSet
) : UserSessionScopeCommon(authenticatedDataSourceSet) {
    override val clientConfig: ClientConfig get() = ClientConfig()
}
