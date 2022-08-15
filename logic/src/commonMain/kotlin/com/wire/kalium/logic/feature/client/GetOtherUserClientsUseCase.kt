package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.OtherUserClients
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold


interface GetOtherUserClientsUseCase {
    suspend operator fun invoke(userId: UserId): GetOtherUserClientsResult
}

internal class GetOtherUserClientsUseCaseImpl(
    private val clientRepository: ClientRepository
) : GetOtherUserClientsUseCase {
    override suspend operator fun invoke(userId: UserId): GetOtherUserClientsResult =
        clientRepository.getClientsByUserId(userId).fold({
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
