package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.self.SelfUserRepository
import com.wire.kalium.logic.functional.Either

class SyncSelfUserUseCase(private val selfUserRepository: SelfUserRepository) {

    suspend operator fun invoke(): Either<CoreFailure, Unit> {
        return selfUserRepository.fetchSelfUser()
    }

}
