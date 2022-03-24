package com.wire.kalium.logic.feature

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.network.api.NonQualifiedUserId

actual class UserSessionScope(
    userId: NonQualifiedUserId, authenticatedDataSourceSet: AuthenticatedDataSourceSet, sessionRepository: SessionRepository
) : UserSessionScopeCommon(userId, authenticatedDataSourceSet, sessionRepository) {
    override val clientConfig: ClientConfig get() = ClientConfig()

    override val protoContentMapper: ProtoContentMapper get() = ProtoContentMapperImpl()

}
