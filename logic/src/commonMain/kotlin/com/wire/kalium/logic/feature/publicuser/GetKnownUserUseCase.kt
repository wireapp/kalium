package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Gets the public user profile of a contact
 */
interface GetKnownUserUseCase {
    /**
     * @param userId the user id of the contact
     * @return the [Flow] of [OtherUser] if successful
     */
    suspend operator fun invoke(userId: UserId): Flow<OtherUser?>
}

internal class GetKnownUserUseCaseImpl internal constructor(
    private val userRepository: UserRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : GetKnownUserUseCase {

    // TODO(qol): Better handle nullable OtherUser?
    override suspend fun invoke(userId: UserId): Flow<OtherUser?> = withContext(dispatchers.default) {
        userRepository.getKnownUser(userId)
    }
}
