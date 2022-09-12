package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.client.ClearClientDataUseCase
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import kotlinx.coroutines.cancel

interface LogoutUseCase {
    suspend operator fun invoke(reason: LogoutReason = LogoutReason.SELF_LOGOUT, isHardLogout: Boolean = false)
}

class LogoutUseCaseImpl @Suppress("LongParameterList") constructor(
    private val logoutRepository: LogoutRepository,
    private val sessionRepository: SessionRepository,
    private val clientRepository: ClientRepository,
    private val userId: QualifiedID,
    private val deregisterTokenUseCase: DeregisterTokenUseCase,
    private val clearClientDataUseCase: ClearClientDataUseCase,
    private val clearUserDataUseCase: ClearUserDataUseCase,
    private val userSessionScopeProvider: UserSessionScopeProvider
) : LogoutUseCase {
    // TODO(refactor): Maybe we can simplify by taking some of the responsibility away from here.
    //                 Perhaps [UserSessionScope] (or another specialised class) can observe
    //                 the [LogoutRepository.observeLogout] and invalidating everything in [CoreLogic] level.
    override suspend operator fun invoke(reason: LogoutReason, isHardLogout: Boolean) {
        deregisterTokenUseCase()
        logoutRepository.logout()
        sessionRepository.logout(userId = userId, reason, isHardLogout)
        clientRepository.clearCurrentClientId()
        if (isHardLogout) {
            clientRepository.clearRetainedClientId()
            clearClientDataUseCase()
            clearUserDataUseCase()
        }
        sessionRepository.updateCurrentSession(userId)
        userSessionScopeProvider.get(userId)?.cancel()
        userSessionScopeProvider.delete(userId)
    }
}
