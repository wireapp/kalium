package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.OtherUserClients
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.api.UserId as NetworkUserID

interface GetOtherUserClientsUseCase {
    suspend operator fun invoke(userId: UserId): GetOtherUserClientsResult
}

internal class GetOtherUserClientsUseCaseImpl(
    private val clientRemoteRepository: ClientRemoteRepository
) : GetOtherUserClientsUseCase {
    override suspend operator fun invoke(userId: UserId): GetOtherUserClientsResult =
        clientRemoteRepository.otherUserClients(NetworkUserID(userId.value, userId.domain)).fold({
            GetOtherUserClientsResult.Failure.UserNotFound
        }, {
            GetOtherUserClientsResult.Success(it)
        })
}

sealed class GetOtherUserClientsResult {
    class Success(val otherUserClients: List<OtherUserClients>) : GetOtherUserClientsResult()

    sealed class Failure : GetOtherUserClientsResult() {
        object UserNotFound : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
