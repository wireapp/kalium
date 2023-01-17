package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Syncs the current user's contacts.
 */
interface SyncContactsUseCase {
    /**
     * @return [Either] [CoreFailure] or [Unit] //fixme: we should not return [Either]
     */
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

class SyncContactsUseCaseImpl internal constructor(
    private val userDataSource: UserRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : SyncContactsUseCase {

    override suspend operator fun invoke(): Either<CoreFailure, Unit> = withContext(dispatchers.default) {
        userDataSource.fetchKnownUsers()
    }

}
