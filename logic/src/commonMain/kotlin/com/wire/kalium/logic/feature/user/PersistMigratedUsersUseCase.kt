package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger

fun interface PersistMigratedUsersUseCase {
    suspend operator fun invoke(users: List<User>): Boolean
}

internal class PersistMigratedUsersUseCaseImpl(
    private val userRepository: UserRepository
) : PersistMigratedUsersUseCase {

    override suspend fun invoke(users: List<User>): Boolean =
        userRepository.insertUsersIfUnknown(users)
            .onFailure { kaliumLogger.e("Error while persisting migrated users $it") }
            .isRight()
}
