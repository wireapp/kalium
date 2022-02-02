package com.wire.kalium.logic.feature

import android.content.Context
import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.sync.WorkScheduler
import com.wire.kalium.persistence.db.Database

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from UserSessionScopeCommon
 */
actual class UserSessionScope(
    private val applicationContext: Context,
    private val session: AuthSession,
    authenticatedDataSourceSet: AuthenticatedDataSourceSet
) : UserSessionScopeCommon(authenticatedDataSourceSet) {
    override val clientConfig: ClientConfig get() = ClientConfig(applicationContext)
    override val database: Database get() = Database(applicationContext, "main.db", "123456789")
}
