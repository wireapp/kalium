package com.wire.kalium.logic.data.logout

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.user.logout.LogoutApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

internal interface LogoutRepository {
    suspend fun observeLogout(): Flow<LogoutReason>
    suspend fun onLogout(reason: LogoutReason)
    suspend fun logout(): Either<CoreFailure, Unit>
}

internal class LogoutDataSource(
    private val logoutApi: LogoutApi,
) : LogoutRepository {

    private val logoutEventsChannel = Channel<LogoutReason>()

    override suspend fun observeLogout(): Flow<LogoutReason> = logoutEventsChannel.consumeAsFlow()

    override suspend fun onLogout(reason: LogoutReason) = logoutEventsChannel.send(reason)

    override suspend fun logout(): Either<CoreFailure, Unit> =
        wrapApiRequest { logoutApi.logout() }

}
