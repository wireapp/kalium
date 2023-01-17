package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext

/**
 * This gets and observes the list of valid accounts, and it's associated team.
 */
interface ObserveValidAccountsUseCase {

    /**
     * @return a [Flow] of the list of valid accounts and their associated team.
     */
    suspend operator fun invoke(): Flow<List<Pair<SelfUser, Team?>>>
}

internal class ObserveValidAccountsUseCaseImpl internal constructor(
    private val sessionRepository: SessionRepository,
    private val userSessionScopeProvider: UserSessionScopeProvider,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveValidAccountsUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun invoke(): Flow<List<Pair<SelfUser, Team?>>> =
        withContext(dispatchers.default) {
            sessionRepository.allValidSessionsFlow().flatMapLatest { accountList ->
                val flowsOfSelfUsers = accountList.map { accountInfo ->
                    userSessionScopeProvider.getOrCreate(accountInfo.userId).let {
                        combine(
                            it.users.getSelfUser(),
                            it.team.getSelfTeamUseCase()
                        ) { selfUser, team -> selfUser to team }
                    }
                }
                combine(flowsOfSelfUsers) { it.asList() }
            }
        }
}
