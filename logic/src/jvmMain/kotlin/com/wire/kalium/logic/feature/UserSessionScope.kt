package com.wire.kalium.logic.feature

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.feature.auth.AuthSession

actual class UserSessionScope(
    session: AuthSession,
    authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    kaliumLogger: KaliumLogger
) : UserSessionScopeCommon(session, authenticatedDataSourceSet, kaliumLogger) {
    override val clientConfig: ClientConfig get() = ClientConfig()

    override val protoContentMapper: ProtoContentMapper get() = ProtoContentMapperImpl()

}
