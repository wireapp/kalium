package com.wire.kalium.logic.feature

import android.content.Context
import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from UserSessionScopeCommon
 */
actual class UserSessionScope(
    private val applicationContext: Context,
    authenticatedDataSourceSet: AuthenticatedDataSourceSet
) : UserSessionScopeCommon(authenticatedDataSourceSet) {

    override val clientConfig: ClientConfig get() = ClientConfig(applicationContext)
}
