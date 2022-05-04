package com.wire.kalium.logic.feature

import android.content.Context
import android.content.SharedPreferences
import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from UserSessionScopeCommon
 */
actual class UserSessionScope(
    private val applicationContext: Context,
    userId: UserId,
    authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    sessionRepository: SessionRepository,
    globalCallManager: GlobalCallManager,
    globalPreferences: KaliumPreferences
) : UserSessionScopeCommon(userId, authenticatedDataSourceSet, sessionRepository, globalCallManager, globalPreferences) {

    override val clientConfig: ClientConfig get() = ClientConfig(applicationContext)

    override val protoContentMapper: ProtoContentMapper get() = ProtoContentMapperImpl()

}
