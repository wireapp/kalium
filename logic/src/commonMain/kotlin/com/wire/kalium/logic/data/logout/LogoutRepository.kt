package com.wire.kalium.logic.data.logout

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.message.CryptoSessionMapper
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.user.logout.LogoutApi
import com.wire.kalium.persistence.daokaliumdb.AccountsDAO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

interface LogoutRepository {

    /**
     * Listen to a logout event.
     * The event caries a [LogoutReason].
     */
    suspend fun observeLogout(): Flow<LogoutReason>

    /**
     * Propagates the logout event and [reason],
     * listenable through [observeLogout]
     */
    suspend fun onLogout(reason: LogoutReason)

    /**
     * Informs the backend about the logout,
     * invalidating the current credentials.
     */
    suspend fun logout(logoutReason: LogoutReason): Either<StorageFailure, Unit>
}

internal class LogoutDataSource(
    private val logoutApi: LogoutApi,
    private val accountsDAO: AccountsDAO,
    private val selfUserId: UserId,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
) : LogoutRepository {

    private val logoutEventsChannel = Channel<LogoutReason>(capacity = Channel.CONFLATED)

    override suspend fun observeLogout(): Flow<LogoutReason> = logoutEventsChannel.receiveAsFlow()

    override suspend fun onLogout(reason: LogoutReason) = logoutEventsChannel.send(reason)
    override suspend fun logout(logoutReason: LogoutReason): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            accountsDAO.markAccountAsInvalid(
                idMapper.toDaoModel(selfUserId),
                sessionMapper.toLogoutReasonEntity(logoutReason)
            )
        }.onSuccess {
            wrapApiRequest { logoutApi.logout() }
        }
}
