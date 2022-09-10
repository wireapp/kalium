package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

interface ObserveValidAccountsUseCase {

    suspend operator fun invoke(): Flow<List<Pair<SelfUser, Team?>>>
}

internal class ObserveValidAccountsUseCaseImpl internal constructor(
    private val sessionRepository: SessionRepository,
    private val userSessionScopeProvider: UserSessionScopeProvider
) : ObserveValidAccountsUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun invoke(): Flow<List<Pair<SelfUser, Team?>>> =
        sessionRepository.allValidSessionsFlow().flatMapLatest { accountList ->
            val flowsOfSelfUsers = accountList.map {
                userSessionScopeProvider.get(it.userId).let {
                    combine(
                        it.users.getSelfUser(),
                        it.team.getSelfTeamUseCase()
                    ) { selfUser, team -> selfUser to team }
                }
            }
            combine(flowsOfSelfUsers) { it.asList() }
        }
}
