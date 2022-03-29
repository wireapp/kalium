package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.session.SessionRepository

actual class AuthenticationScope(
    clientLabel: String,
    sessionRepository: SessionRepository
) : AuthenticationScopeCommon(clientLabel, sessionRepository) {

}
