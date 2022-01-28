package com.wire.kalium.logic.feature

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.persistence.db.Database

actual class UserSessionScope(
    authenticatedDataSourceSet: AuthenticatedDataSourceSet
) : UserSessionScopeCommon(authenticatedDataSourceSet) {
    override val clientConfig: ClientConfig get() = ClientConfig()
    override val database: Database
        get() = Database()
}
