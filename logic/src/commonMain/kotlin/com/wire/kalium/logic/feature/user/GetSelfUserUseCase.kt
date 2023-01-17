package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for retrieving the current user.
 * fixme: Rename to ObserveSelfUserUseCase
 */
interface GetSelfUserUseCase {

    /**
     * @return a [Flow] of the current user [SelfUser]
     */
    suspend operator fun invoke(): Flow<SelfUser>

}

internal class GetSelfUserUseCaseImpl internal constructor(
    private val userRepository: UserRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : GetSelfUserUseCase {

    override suspend operator fun invoke(): Flow<SelfUser> = withContext(dispatchers.default) {
        userRepository.observeSelfUser()
    }
}
