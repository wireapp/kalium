package com.wire.kalium.logic.data.logout

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.user.logout.LogoutApi

interface LogoutRepository {
    suspend fun logout(): Either<CoreFailure, Unit>
}

internal class LogoutDataSource(
    private val logoutApi: LogoutApi,
) : LogoutRepository {
    override suspend fun logout(): Either<NetworkFailure, Unit> =
        wrapApiRequest { logoutApi.logout() }
}
