package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.session.SessionRepository

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from AuthenticationScopeCommon
 */
actual class AuthenticationScope(
    clientLabel: String,
    sessionRepository: SessionRepository
) : AuthenticationScopeCommon(clientLabel, sessionRepository) {

}
