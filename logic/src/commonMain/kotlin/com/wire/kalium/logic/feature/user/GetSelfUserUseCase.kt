package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.self.SelfUserRepository
import com.wire.kalium.logic.data.user.self.model.SelfUser
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow

class GetSelfUserUseCase(
    private val selfUserRepository: SelfUserRepository,
    private val syncManager: SyncManager
) {

    suspend operator fun invoke(): Flow<SelfUser> {
        syncManager.waitUntilSlowSyncCompletion()
        return selfUserRepository.getSelfUser()
    }
}
