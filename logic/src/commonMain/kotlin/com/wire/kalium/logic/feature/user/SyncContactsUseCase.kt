package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.self.SelfUserRepository
import com.wire.kalium.logic.functional.Either

interface SyncContactsUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

class SyncContactsUseCaseImpl(private val userDataSource: SelfUserRepository) : SyncContactsUseCase {

    override suspend operator fun invoke(): Either<CoreFailure, Unit> {
        return userDataSource.fetchKnownUsers()
    }

}
