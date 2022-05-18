package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow

class GetSelfUserUseCase(private val userRepository: UserRepository,
                         private val syncManager: SyncManager
) {

    suspend operator fun invoke(): Flow<SelfUser> {
        syncManager.waitForSyncToComplete()
        return userRepository.getSelfUser()
    }
}
