package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Persist migrated users from old datasource
 */
fun interface PersistMigratedUsersUseCase {
    suspend operator fun invoke(users: List<User>): Boolean
}

internal class PersistMigratedUsersUseCaseImpl(
    private val userRepository: UserRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : PersistMigratedUsersUseCase {

    override suspend fun invoke(users: List<User>): Boolean = withContext(dispatchers.default) {
        userRepository.insertUsersIfUnknown(users)
            .onFailure { kaliumLogger.e("Error while persisting migrated users $it") }
            .isRight()
    }
}
