package com.wire.kalium.logic.feature

import android.content.Context
import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.feature.auth.AuthSession

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from UserSessionScopeCommon
 */
actual class UserSessionScope(
    private val applicationContext: Context,
    private val session: AuthSession,
    authenticatedDataSourceSet: AuthenticatedDataSourceSet,
) : UserSessionScopeCommon(session, authenticatedDataSourceSet) {

    override val clientConfig: ClientConfig get() = ClientConfig(applicationContext)

    override val protoContentMapper: ProtoContentMapper get() = ProtoContentMapperImpl()

}
