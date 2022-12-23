package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either

/**
 * This use case will sync the current user with the backend.
 */
class SyncSelfUserUseCase internal constructor(
    private val userRepository: UserRepository
) {
    /**
     * @return [Either] [CoreFailure] or [Unit] //fixme: we should not return [Either]
     */
    suspend operator fun invoke(): Either<CoreFailure, Unit> = userRepository.fetchSelfUser()
}
