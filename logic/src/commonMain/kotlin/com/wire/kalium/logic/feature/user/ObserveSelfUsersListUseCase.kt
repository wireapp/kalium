package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

interface ObserveSelfUsersListUseCase {

    suspend operator fun invoke(): Flow<List<SelfUser>>
}

internal class ObserveSelfUsersListUseCaseImpl internal constructor(
    private val sessionRepository: SessionRepository,
    private val userSessionScopeProvider: UserSessionScopeProvider
) : ObserveSelfUsersListUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun invoke(): Flow<List<SelfUser>> =
        sessionRepository.allValidSessionsFlow().flatMapLatest {
            val flowsOfSelfUsers = it.map {
                userSessionScopeProvider.get(it.session.userId).users.getSelfUser()
            }
            combine(flowsOfSelfUsers) { it.asList() }
        }
}
