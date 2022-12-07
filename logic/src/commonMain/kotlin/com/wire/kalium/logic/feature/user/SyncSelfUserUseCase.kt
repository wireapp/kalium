package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either

class SyncSelfUserUseCase internal constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(): Either<CoreFailure, Unit> = userRepository.fetchSelfUser()
}
